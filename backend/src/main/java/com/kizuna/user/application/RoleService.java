package com.kizuna.user.application;

import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.IntegrityViolations;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.user.api.dto.RoleCreateRequest;
import com.kizuna.user.api.dto.RoleResponse;
import com.kizuna.user.api.dto.RoleSummaryResponse;
import com.kizuna.user.api.dto.RoleUpdateRequest;
import com.kizuna.user.domain.LastRoleManageHolderException;
import com.kizuna.user.domain.Permission;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.PermissionRepository;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.Role;
import com.kizuna.user.domain.RoleInUseException;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StaleRoleUpdateException;
import com.kizuna.user.domain.SystemRoleImmutableException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ロール管理ユースケース（RBAC）。権限は目録（{@link Permission}）に実在するコードのみを受け付け、応答も id ではなくコードで返す — 権限目録は播種で固定される正本であり
 * API 面に id を露出しない。
 *
 * <p>平台既定ロールは変更・削除を拒否し（400）、授与中のロールの削除は 409 で拒否する。
 */
@Service
@RequiredArgsConstructor
public class RoleService {

  /** 不減零の母集団を定める権限コード。目録行そのものが守衛の直列化点でもある。 */
  private static final String ROLE_MANAGE = PermissionCode.ROLE_MANAGE.name();

  private final RoleRepository roleRepository;
  private final PermissionRepository permissionRepository;
  private final PlatformUserRepository platformUserRepository;

  /** 一覧は権限の個数だけを返す要約。権限コードの列挙が要るのは編集時のみで、それは {@link #get(Long)} が担う。 */
  @Transactional(readOnly = true)
  public List<RoleSummaryResponse> list() {
    return roleRepository.findAllSummaries().stream()
        .map(
            summary ->
                new RoleSummaryResponse(
                    summary.getId(),
                    summary.getName(),
                    Boolean.TRUE.equals(summary.getSystemRole()),
                    summary.getPermissionCount()))
        .toList();
  }

  @Transactional(readOnly = true)
  public RoleResponse get(Long id) {
    Role role = roleRepository.findById(id).orElseThrow(() -> notFound(id));
    return toResponse(role, codesById(role.getPermissionIds()));
  }

  @Transactional
  public RoleResponse create(RoleCreateRequest req) {
    Map<Long, String> codesById = requirePermissions(req.getPermissions());
    Role role =
        Role.builder()
            .name(req.getName())
            .systemRole(false)
            .permissionIds(codesById.keySet())
            .build();
    return toResponse(save(role), codesById);
  }

  @Transactional
  public RoleResponse update(Long id, RoleUpdateRequest req) {
    Map<Long, String> codesById = requirePermissions(req.getPermissions());
    Role role = roleRepository.findById(id).orElseThrow(() -> notFound(id));
    // 陳腐化した編集フォームの提出は JPA の @Version では捕まらない（再読込後の正当な更新に見える）
    // ため、応答で往復させた version を明示比対して 409 で拒否する。
    if (!role.getVersion().equals(req.getVersion())) {
      throw new StaleRoleUpdateException("他の管理者が更新しました。最新の内容を確認してください");
    }
    requireRoleManageHolderRemains(role, req.getPermissions());
    role.rename(req.getName());
    role.replacePermissions(codesById.keySet());
    return toResponse(save(role), codesById);
  }

  /**
   * 不減零（ADR 0020 の守衛 G5）。ROLE_MANAGE を含むロールからそれを外す編集は、そのロールしか持たない保持者を一撃で全員 非保持者にする。有効な ROLE_MANAGE
   * 実効保持者が 0 になるならその編集を拒む。
   *
   * <p>母集団を減らす編集だけが共有の直列化点（{@link PermissionRepository#lockIdByCode}、取り直しの理由もそちら）を押さえ、
   * 押さえた後に母集団を取り直して判定し直す。平台既定ロールは改廃自体を {@link Role#replacePermissions} が拒むため、押さえずに抜ける。
   */
  private void requireRoleManageHolderRemains(Role role, Set<String> newPermissionCodes) {
    if (Boolean.TRUE.equals(role.getSystemRole())
        || newPermissionCodes.contains(ROLE_MANAGE)
        || !suppliesRoleManage(role)) {
      return;
    }
    permissionRepository.lockIdByCode(ROLE_MANAGE);
    Set<Long> roleManageRoleIds = roleRepository.findIdsByPermissionCode(ROLE_MANAGE);
    if (!roleManageRoleIds.contains(role.getId())) {
      return;
    }
    Set<Long> remaining = new HashSet<>(roleManageRoleIds);
    remaining.remove(role.getId());
    // 空集合は HQL の in へ渡さない（残る供給元が無いので保持者も居ない）。
    if (!remaining.isEmpty()
        && !platformUserRepository.findEnabledRoleHolderIds(remaining).isEmpty()) {
      return;
    }
    // 編集前から母集団が 0 の配備では、この編集は母集団を減らさない — 悪化させないので通す。
    if (platformUserRepository.findEnabledRoleHolderIds(Set.of(role.getId())).isEmpty()) {
      return;
    }
    throw new LastRoleManageHolderException("最後の管理権限保持者が居なくなるため、このロールから管理権限を外すことはできません");
  }

  private boolean suppliesRoleManage(Role role) {
    return roleRepository.findIdsByPermissionCode(ROLE_MANAGE).contains(role.getId());
  }

  @Transactional
  public void delete(Long id) {
    Role role = roleRepository.findById(id).orElseThrow(() -> notFound(id));
    if (Boolean.TRUE.equals(role.getSystemRole())) {
      throw new SystemRoleImmutableException("平台既定ロールは削除できません");
    }
    if (platformUserRepository.existsByRoleId(id)) {
      throw new RoleInUseException("授与中のロールは削除できません");
    }
    try {
      // 事前検証と削除の間に授与が割り込む競合は t_user_roles の RESTRICT が拾う。commit 時まで
      // 遅らせると 500 になるため、ここで flush して事前検証と同じ 409 へ揃える。
      roleRepository.delete(role);
      roleRepository.flush();
    } catch (DataIntegrityViolationException ex) {
      // 授与 FK 違反は全域ハンドラでは 409 にならない（一意違反のみが兜底の対象）ため、ここで写像する。
      // 授与 FK 以外の整合性違反は実装欠陥であり、握りつぶさず全域ハンドラの分類に委ねる。
      throw IntegrityViolations.translate(
          ex,
          Map.of(
              DbConstraint.FK_T_USER_ROLES_ROLE, () -> new RoleInUseException("授与中のロールは削除できません")));
    }
  }

  private static NotFoundException notFound(Long id) {
    return new NotFoundException("ロールが見つかりません: " + id);
  }

  /** 指定コードが全て権限目録に実在することを検証し、id→コードの対応を返す（応答組立にも使う）。 */
  private Map<Long, String> requirePermissions(Set<String> codes) {
    List<Permission> found = permissionRepository.findByCodeIn(codes);
    if (found.size() != codes.size()) {
      throw new ServiceException("指定された権限が存在しません");
    }
    return found.stream().collect(Collectors.toMap(Permission::getId, Permission::getCode));
  }

  /**
   * 保存時の名称一意制約違反（並行作成レース）を事前チェックと同じ 400 へ変換する。それ以外の整合性違反は実装欠陥であり（権限は事前検証済み）、 握りつぶさず全域ハンドラの分類に委ねる。
   */
  private Role save(Role role) {
    return IntegrityMappedSaves.save(
        roleRepository,
        role,
        Map.of(DbConstraint.UQ_T_ROLES_NAME, () -> new ServiceException("このロール名は既に使われています")));
  }

  private Map<Long, String> codesById(Set<Long> permissionIds) {
    return permissionRepository.findAllById(permissionIds).stream()
        .collect(Collectors.toMap(Permission::getId, Permission::getCode));
  }

  private static RoleResponse toResponse(Role role, Map<Long, String> codesById) {
    return new RoleResponse(
        role.getId(),
        role.getName(),
        Boolean.TRUE.equals(role.getSystemRole()),
        role.getPermissionIds().stream().map(codesById::get).sorted().toList(),
        role.getVersion());
  }
}
