package com.kizuna.user.application;

import com.kizuna.shared.exception.ServiceException;
import com.kizuna.user.api.dto.RoleCreateRequest;
import com.kizuna.user.api.dto.RoleResponse;
import com.kizuna.user.api.dto.RoleUpdateRequest;
import com.kizuna.user.domain.Permission;
import com.kizuna.user.domain.PermissionRepository;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.Role;
import com.kizuna.user.domain.RoleInUseException;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StaleRoleUpdateException;
import com.kizuna.user.domain.SystemRoleImmutableException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
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

  private static final String NAME_UNIQUE_CONSTRAINT = "uq_t_roles_name";

  private final RoleRepository roleRepository;
  private final PermissionRepository permissionRepository;
  private final PlatformUserRepository platformUserRepository;

  @Transactional(readOnly = true)
  public List<RoleResponse> list() {
    List<Role> roles = roleRepository.findAll(Sort.by("name"));
    Set<Long> permissionIds =
        roles.stream()
            .flatMap(role -> role.getPermissionIds().stream())
            .collect(Collectors.toSet());
    Map<Long, String> codesById = codesById(permissionIds);
    return roles.stream().map(role -> toResponse(role, codesById)).toList();
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
  public Optional<RoleResponse> update(Long id, RoleUpdateRequest req) {
    Map<Long, String> codesById = requirePermissions(req.getPermissions());
    return roleRepository
        .findById(id)
        .map(
            role -> {
              // 陳腐化した編集フォームの提出は JPA の @Version では捕まらない（再読込後の正当な更新に見える）
              // ため、応答で往復させた version を明示比対して 409 で拒否する。
              if (!role.getVersion().equals(req.getVersion())) {
                throw new StaleRoleUpdateException("他の管理者が更新しました。最新の内容を確認してください");
              }
              role.rename(req.getName());
              role.replacePermissions(codesById.keySet());
              return toResponse(save(role), codesById);
            });
  }

  /** 削除する。対象が存在しなければ false（呼び出し側が 404 にする）。 */
  @Transactional
  public boolean delete(Long id) {
    Optional<Role> found = roleRepository.findById(id);
    if (found.isEmpty()) {
      return false;
    }
    Role role = found.get();
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
      throw new RoleInUseException("授与中のロールは削除できません");
    }
    return true;
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
   * 保存時の整合性違反を 400 へ変換する（現状の経路は名称の一意制約違反のみ — 権限は事前検証済み）。
   *
   * <p>権限集合の @ElementCollection 行はトランザクション commit 時に flush されるため、{@code save} だけでは違反がこの try を突き抜けて
   * 500 になる。{@code saveAndFlush} で違反をここで顕在化させる。
   */
  private Role save(Role role) {
    try {
      return roleRepository.saveAndFlush(role);
    } catch (DataIntegrityViolationException ex) {
      String cause = ex.getMostSpecificCause().getMessage();
      if (cause != null && cause.contains(NAME_UNIQUE_CONSTRAINT)) {
        throw new ServiceException("このロール名は既に使われています");
      }
      throw new ServiceException("指定された権限が存在しません");
    }
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
