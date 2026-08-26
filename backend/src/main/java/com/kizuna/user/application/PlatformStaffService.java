package com.kizuna.user.application;

import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.user.api.dto.PlatformStaffCreateRequest;
import com.kizuna.user.api.dto.PlatformStaffResponse;
import com.kizuna.user.api.dto.PlatformStaffUpdateRequest;
import com.kizuna.user.domain.DuplicateStaffEmailException;
import com.kizuna.user.domain.InvalidRoleGrantException;
import com.kizuna.user.domain.InvalidStoreScopeException;
import com.kizuna.user.domain.LastRoleManageHolderException;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.PermissionRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.PlatformUserResumed;
import com.kizuna.user.domain.PlatformUserStopped;
import com.kizuna.user.domain.Role;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.SelfStopNotAllowedException;
import com.kizuna.user.domain.StaleStaffUpdateException;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.EscapeCharacter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理者（HQ 側ロール保持者）の授権管理ユースケース。対象は本人種別 STAFF のうち HQ 側ロールを 1 つ以上持つ者に限る — CAST/MEMBER
 * は専用フローが、店舗側ロールのみの利用者は店舗スタッフ管理が扱う（ADR 0020）。HQ 側ロールの定義は {@link RoleRepository#findHqRoleIds} が単源。
 */
@Service
@RequiredArgsConstructor
public class PlatformStaffService {

  /** LIKE パターンのエスケープ規則。派生クエリが内部で使うものと同一で、手書きの cb.like にも同じ規則を適用する。 */
  private static final EscapeCharacter LIKE_ESCAPE = EscapeCharacter.DEFAULT;

  private final PlatformUserRepository repository;
  private final RoleRepository roleRepository;
  private final PermissionRepository permissionRepository;
  private final PasswordEncoder passwordEncoder;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional(readOnly = true)
  public Page<PlatformStaffResponse> list(String search, Long storeId, Pageable pageable) {
    Page<PlatformUser> staff =
        repository.findAll(staffSpec(search, storeId, roleRepository.findHqRoleIds()), pageable);
    Set<Long> allRoleIds =
        staff.getContent().stream()
            .flatMap(user -> user.getRoleIds().stream())
            .collect(Collectors.toSet());
    Map<Long, String> roleNames = roleNamesOf(allRoleIds);
    return staff.map(user -> toResponse(user, roleNames));
  }

  /**
   * 一覧の対象は本人種別 STAFF かつ HQ 側ロールを 1 つ以上持つ行に限る。検索語は表示名とメールアドレスを横断する部分一致。店舗 id
   * は「その店舗を担当範囲に含む」行への絞り込みで、ALL_STORES は個別 id を持たないまま全店舗を覆うため常に該当させる。
   *
   * <p>null の条件は述語を生成しない（JPQL の ":param is null or ..." パターンは PostgreSQL の null パラメータ型推論で 500 になるため
   * Specification で組み立てる）。
   */
  private static Specification<PlatformUser> staffSpec(
      String search, Long storeId, Set<Long> hqRoleIds) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("userType"), UserType.STAFF));
      // ロール集合は @ElementCollection のため、店舗集合と同様に member of（相関 exists）で組んで親行を増やさない。
      // HQ 側ロールは高々数件なので、id ごとの述語を OR で並べても副問い合わせの本数は実用上問題にならない。
      predicates.add(
          cb.or(
              hqRoleIds.stream()
                  .map(roleId -> cb.isMember(roleId, root.<Set<Long>>get("roleIds")))
                  .toArray(Predicate[]::new)));
      if (search != null) {
        char escape = LIKE_ESCAPE.getEscapeCharacter();
        String pattern = "%" + LIKE_ESCAPE.escape(search.toLowerCase(Locale.ROOT)) + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("displayName")), pattern, escape),
                cb.like(cb.lower(root.get("email")), pattern, escape)));
      }
      if (storeId != null) {
        // 担当店舗集合は @ElementCollection のため、member of は Hibernate が相関副問い合わせ（exists）へ展開する。
        // 親行を結合で増やさないので、ページングの件数・境界に影響しない。
        predicates.add(
            cb.or(
                cb.equal(root.get("storeScopeType"), StoreScopeType.ALL_STORES),
                cb.isMember(storeId, root.get("storeIds"))));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  @Transactional
  public PlatformStaffResponse create(PlatformStaffCreateRequest req) {
    Map<Long, String> roleNames = requireRoles(req.getRoleIds());
    requireHqRole(req.getRoleIds(), roleRepository.findHqRoleIds());
    if (repository.findByEmail(req.getEmail().toLowerCase(Locale.ROOT)).isPresent()) {
      throw new DuplicateStaffEmailException("このメールアドレスは既に登録されています");
    }
    PlatformUser user =
        PlatformUser.builder()
            .email(req.getEmail())
            .password(passwordEncoder.encode(req.getPassword()))
            .displayName(req.getDisplayName())
            .enabled(true)
            .userType(UserType.STAFF)
            .roleIds(req.getRoleIds())
            .storeScopeType(req.getStoreScopeType())
            .storeIds(req.getStoreIds())
            .build();
    return toResponse(save(user), roleNames);
  }

  /** 1 件取得。編集中に競合（409）が起きたとき、一覧の現在ページに対象が居なくても最新の版を取り直せるようにするための経路。 */
  @Transactional(readOnly = true)
  public PlatformStaffResponse get(Long id) {
    PlatformUser user = requireManagedStaff(id, roleRepository.findHqRoleIds());
    return toResponse(user, roleNamesOf(user.getRoleIds()));
  }

  @Transactional
  public PlatformStaffResponse update(Long id, PlatformStaffUpdateRequest req, String actorEmail) {
    Map<Long, String> roleNames = requireRoles(req.getRoleIds());
    Set<Long> hqRoleIds = roleRepository.findHqRoleIds();
    requireHqRole(req.getRoleIds(), hqRoleIds);
    PlatformUser user = requireManagedStaff(id, hqRoleIds);
    // 陳腐化した編集フォームの提出は JPA の @Version では捕まらない（再読込後の正当な更新に見える）
    // ため、応答で往復させた version を明示比対して 409 で拒否する。
    if (!user.getVersion().equals(req.getVersion())) {
      throw new StaleStaffUpdateException("他の管理者が更新しました。最新の内容を確認してください");
    }
    // 自分自身を停止すると自らのセッションも即時失効し、以後の操作ができなくなる（サポート経路がない自己ロックアウト）ため拒否する。
    if (Boolean.FALSE.equals(req.getEnabled()) && user.getEmail().equals(actorEmail)) {
      throw new SelfStopNotAllowedException("自分自身を停止することはできません");
    }
    requireRoleManageHolderRemains(user, req);
    user.reassignGrants(req.getRoleIds(), req.getStoreScopeType(), req.getStoreIds());
    // enabled の遷移（null=現状維持）。停止は行を残し、過去の実行主体の記録を保持する。
    if (Boolean.FALSE.equals(req.getEnabled()) && user.getEnabled()) {
      user.stop();
    }
    if (Boolean.TRUE.equals(req.getEnabled()) && !user.getEnabled()) {
      user.resume();
    }
    // 失効の即時反映は「本リクエストが停止/再開を明示的に要求したか」で判定する（現在状態との差分ではない）。
    // AFTER_COMMIT の Redis 書き込みが失敗して 500 になっても、最新 version を取り直して同じ停止要求を
    // 再送すれば失効が書き直されるようにするための冪等化（差分語義だと再送時には既に enabled=false の
    // ためイベントが発行されず、resume→stop 以外に復旧手段が無くなる）。version は楽観ロックで
    // commit 済みの更新ぶん進んでいるため、再送には GET の取り直しが要る点に注意。
    if (Boolean.FALSE.equals(req.getEnabled())) {
      eventPublisher.publishEvent(new PlatformUserStopped(user.getEmail()));
    }
    if (Boolean.TRUE.equals(req.getEnabled())) {
      eventPublisher.publishEvent(new PlatformUserResumed(user.getEmail()));
    }
    return toResponse(save(user), roleNames);
  }

  /**
   * 管理者管理の対象行を取り出す。
   *
   * <p>本人種別がスタッフ以外（CAST/MEMBER）の行と、HQ 側ロールを 1 つも持たない行は、存在しても本 API の対象外として 「見つからない」に倒す（一覧と同じ絞り）。
   * 対象外と不在を呼出側から区別できないようにするため、両者は同一の応答になる — 店舗側の利用者の在否をこの面から列挙させない。
   */
  private PlatformUser requireManagedStaff(Long id, Set<Long> hqRoleIds) {
    return repository
        .findById(id)
        .filter(user -> user.getUserType() == UserType.STAFF)
        .filter(user -> holdsAny(user.getRoleIds(), hqRoleIds))
        .orElseThrow(() -> new NotFoundException("管理者が見つかりません: " + id));
  }

  /**
   * 授与後も HQ 側ロール保持者であることを要求する。素通しにすると、作成した直後に一覧から消えるアカウントが作れてしまい、
   * 店舗側ロールのみへ降ろされた利用者はこの面から二度と辿り着けなくなる（店舗スタッフ管理の領分・ADR 0020）。
   */
  private static void requireHqRole(Set<Long> roleIds, Set<Long> hqRoleIds) {
    if (!holdsAny(roleIds, hqRoleIds)) {
      throw new InvalidRoleGrantException("管理者にはプラットフォーム権限を含むロールを 1 つ以上付与してください");
    }
  }

  /**
   * 不減零（ADR 0020 の守衛 G5）。有効な ROLE_MANAGE 実効保持者が 0 になる停止・剥奪を拒む。判定を役職名（HQ_ADMIN）でなく 実効権限で行うのは、管理が
   * ROLE_MANAGE を含む自作ロールへ移った配備でも正しく数えるためである。
   *
   * <p>母集団を減らす更新だけが共有の直列化点（{@link PermissionRepository#lockIdByCode}）を押さえ、押さえた後に ROLE_MANAGE
   * を含むロール集合を取り直して判定し直す — 押さえる前に読んだ集合のままだと、並行するロール定義の編集が そのロールから ROLE_MANAGE
   * を外し終えていても保持者として数え続け、双方の検査が通る。
   *
   * <p>そのうえで母集団の行も押さえて数え直す。押さえる問い合わせ自身の結果は待つ前のスナップショットのままで、待っている間に 確定した降格を見ない（{@link
   * PlatformUserRepository#lockEnabledRoleHolderIds}）。目録行の直列化点があれば行ロックは冗長だが、 母集団の行を押さえている事実自体が
   * {@code PlatformStaffManagementIT} の実測の対象なので残している。
   */
  private void requireRoleManageHolderRemains(PlatformUser user, PlatformStaffUpdateRequest req) {
    if (!reducesRoleManageHolders(user, req, roleManageRoleIds())) {
      return;
    }
    permissionRepository.lockIdByCode(PermissionCode.ROLE_MANAGE.name());
    Set<Long> roleManageRoleIds = roleManageRoleIds();
    if (!reducesRoleManageHolders(user, req, roleManageRoleIds)) {
      return;
    }
    repository.lockEnabledRoleHolderIds(roleManageRoleIds);
    List<Long> holders = repository.findEnabledRoleHolderIds(roleManageRoleIds);
    if (holders.size() == 1 && holders.contains(user.getId())) {
      throw new LastRoleManageHolderException("最後の管理権限保持者を停止・降格することはできません");
    }
  }

  private Set<Long> roleManageRoleIds() {
    return roleRepository.findIdsByPermissionCode(PermissionCode.ROLE_MANAGE.name());
  }

  /** 対象が今そこに居て、更新後に居なくなるか。居ないなら、この更新で母集団は減らない。 */
  private static boolean reducesRoleManageHolders(
      PlatformUser user, PlatformStaffUpdateRequest req, Set<Long> roleManageRoleIds) {
    if (roleManageRoleIds.isEmpty()) {
      return false;
    }
    boolean wasHolder = user.getEnabled() && holdsAny(user.getRoleIds(), roleManageRoleIds);
    // enabled は null で現状維持。ここへ来る対象は有効なので、明示的な false だけが停止になる。
    boolean staysHolder =
        !Boolean.FALSE.equals(req.getEnabled()) && holdsAny(req.getRoleIds(), roleManageRoleIds);
    return wasHolder && !staysHolder;
  }

  private static boolean holdsAny(Set<Long> roleIds, Set<Long> targetRoleIds) {
    return roleIds.stream().anyMatch(targetRoleIds::contains);
  }

  /** 指定 id のロールが全て実在することを検証し、id→名称の対応を返す（応答組立にも使う）。 */
  private Map<Long, String> requireRoles(Set<Long> roleIds) {
    Map<Long, String> names = roleNamesOf(roleIds);
    if (names.size() != roleIds.size()) {
      throw new ServiceException("指定されたロールが存在しません");
    }
    return names;
  }

  private Map<Long, String> roleNamesOf(Set<Long> roleIds) {
    return roleRepository.findAllById(roleIds).stream()
        .collect(Collectors.toMap(Role::getId, Role::getName));
  }

  /**
   * 保存時の整合性違反を制約名で分類する。email 一意制約違反（同一メール二重送信レース）は重複エラー、店舗 FK 違反（存在しない店舗 id）は店舗エラー、ロール FK
   * 違反（requireRoles 通過後の並行ロール削除）はロール不存在エラーへ変換する（いずれも 400）。それ以外の整合性違反は実装欠陥であり、握りつぶさず全域ハンドラの分類に委ねる。
   */
  private PlatformUser save(PlatformUser user) {
    return IntegrityMappedSaves.save(
        repository,
        user,
        Map.of(
            DbConstraint.UQ_T_USERS_EMAIL,
            () -> new DuplicateStaffEmailException("このメールアドレスは既に登録されています"),
            DbConstraint.FK_T_USER_STORES_STORE,
            () -> new InvalidStoreScopeException("指定された店舗が存在しません"),
            DbConstraint.FK_T_USER_ROLES_ROLE,
            () -> new ServiceException("指定されたロールが存在しません")));
  }

  private static PlatformStaffResponse toResponse(PlatformUser user, Map<Long, String> roleNames) {
    List<PlatformStaffResponse.RoleRef> roles =
        user.getRoleIds().stream()
            .map(id -> new PlatformStaffResponse.RoleRef(id, roleNames.get(id)))
            .sorted(
                Comparator.comparing(
                    PlatformStaffResponse.RoleRef::name,
                    Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    return new PlatformStaffResponse(
        user.getId(),
        user.getEmail(),
        user.getDisplayName(),
        user.getEnabled(),
        roles,
        user.getStoreScopeType(),
        new HashSet<>(user.getStoreIds()),
        user.getVersion());
  }
}
