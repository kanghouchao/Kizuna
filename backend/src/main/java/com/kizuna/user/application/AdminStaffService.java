package com.kizuna.user.application;

import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.IntegrityViolations;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.user.api.dto.AdminStaffCreateRequest;
import com.kizuna.user.api.dto.AdminStaffResponse;
import com.kizuna.user.api.dto.AdminStaffUpdateRequest;
import com.kizuna.user.api.dto.RoleSummaryResponse;
import com.kizuna.user.domain.DuplicateStaffEmailException;
import com.kizuna.user.domain.HqPasswordResetNotAllowedException;
import com.kizuna.user.domain.InvalidRoleGrantException;
import com.kizuna.user.domain.InvalidStoreScopeException;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.Role;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.SelfPasswordResetNotAllowedException;
import com.kizuna.user.domain.SelfStopNotAllowedException;
import com.kizuna.user.domain.StaffOutOfDelegationScopeException;
import com.kizuna.user.domain.StaleStaffUpdateException;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.EscapeCharacter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 従業員管理ユースケース。対象は本人種別 STAFF の全アカウントで、CAST / MEMBER は権限モデルの外なので在否も出さない。
 *
 * <p>行使者の店舗集合は要求ごとに解決された作用域（{@link StoreScopeHolder}）から、権限並集は本人の行から取る。User は StoreScopedEntity
 * ではないため、店舗境界は本クラスの明示検証が唯一の担保である。
 *
 * <p>委譲の守衛は 2 条のみ — 付与するロールの権限並集 ⊆ 行使者の権限並集、対象の店舗集合 ⊆ 行使者の店舗集合。編集・停止・再開・再設定の対象も 同じ 2 条（{@link
 * #editableBy}）で判定する。同格の相互保護のような硬い規則は置かず、既定ロールの編集で運用が決める。
 *
 * <p>残る名指しの守衛は 3 つ: 自分自身の停止と再設定、最後の ROLE_MANAGE 保持者の停止・降格（{@link RoleManageHolderGuard}）、
 * 管理者（ROLE_MANAGE 保持者）のパスワード代理再設定。
 */
@Service
@RequiredArgsConstructor
public class AdminStaffService {

  /** LIKE パターンのエスケープ規則。派生クエリが内部で使うものと同一で、手書きの cb.like にも同じ規則を適用する。 */
  private static final EscapeCharacter LIKE_ESCAPE = EscapeCharacter.DEFAULT;

  /** 仮パスワードの生成源。予測可能な乱数だと発行直後の乗っ取りを許すため暗号論的乱数に限る。 */
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final BusinessAudit businessAudit;
  private final UserRepository repository;
  private final RoleRepository roleRepository;
  private final RoleManageHolderGuard roleManageHolderGuard;
  private final PasswordEncoder passwordEncoder;
  private final CredentialOperations credentialOperations;
  private final StoreScopeHolder storeScopeHolder;

  /** 行使者の有効権限並集（ロールの権限 id の和）・ROLE_MANAGE の保持・店舗集合。 */
  record Actor(User user, Set<Long> permissionIds, boolean definesRoles, StoreScope scope) {

    /** 付与・編集の対象にできる権限集合か。ロールを定義できる者には権限の部分集合を課さない。 */
    boolean covers(Set<Long> permissionIds) {
      return definesRoles || this.permissionIds.containsAll(permissionIds);
    }
  }

  @Transactional(readOnly = true)
  public Page<AdminStaffResponse> list(
      String search, Long storeId, Pageable pageable, String actorEmail) {
    Actor actor = actor(actorEmail);
    Page<User> staff = repository.findAll(staffSpec(search, storeId, actor.scope()), pageable);
    Map<Long, Role> roles =
        rolesOf(
            staff.getContent().stream()
                .flatMap(user -> user.getRoleIds().stream())
                .collect(Collectors.toSet()));
    return staff.map(user -> toResponse(user, roles, actor));
  }

  /**
   * 一覧の対象は本人種別 STAFF のうち、行使者の店舗集合と交わる者（ALL_STORES の行使者は全員、ALL_STORES の対象は常に該当）。検索語は表示名と
   * メールアドレスを横断する部分一致。店舗 id は「その店舗を担当範囲に含む」行への絞り込み。
   *
   * <p>null の条件は述語を生成しない（JPQL の ":param is null or ..." パターンは PostgreSQL の null パラメータ型推論で 500 になるため
   * Specification で組み立てる）。
   */
  private static Specification<User> staffSpec(String search, Long storeId, StoreScope scope) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("userType"), UserType.STAFF));
      if (!scope.allStores()) {
        predicates.add(authorizesAnyOf(root, cb, scope.storeIds()));
      }
      if (search != null) {
        char escape = LIKE_ESCAPE.getEscapeCharacter();
        String pattern = "%" + LIKE_ESCAPE.escape(search.toLowerCase(Locale.ROOT)) + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("displayName")), pattern, escape),
                cb.like(cb.lower(root.get("email")), pattern, escape)));
      }
      if (storeId != null) {
        predicates.add(authorizesAnyOf(root, cb, Set.of(storeId)));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  /** 担当店舗集合は @ElementCollection のため member of は相関副問い合わせへ展開され、親行を増やさずページングを保つ。 */
  private static Predicate authorizesAnyOf(
      Root<User> root, CriteriaBuilder cb, Collection<Long> storeIds) {
    List<Predicate> any = new ArrayList<>();
    any.add(cb.equal(root.get("storeScopeType"), StoreScopeType.ALL_STORES));
    for (Long storeId : storeIds) {
      any.add(cb.isMember(storeId, root.<Set<Long>>get("storeIds")));
    }
    return cb.or(any.toArray(new Predicate[0]));
  }

  /** 1 件取得。編集中に競合（409）が起きたとき、一覧の現在ページに対象が居なくても最新の版を取り直せるようにするための経路。 */
  @Transactional(readOnly = true)
  public AdminStaffResponse get(Long id, String actorEmail) {
    Actor actor = actor(actorEmail);
    User user = requireVisible(id, actor);
    return toResponse(user, rolesOf(user.getRoleIds()), actor);
  }

  /** 行使者が付与できるロールの目録 — 権限集合が行使者の権限並集に収まるもの（ROLE_MANAGE 保持者は全ロール）。判定の単源をサーバに置く。 */
  @Transactional(readOnly = true)
  public List<RoleSummaryResponse> grantableRoles(String actorEmail) {
    Actor actor = actor(actorEmail);
    return roleRepository.findAll().stream()
        .filter(role -> actor.covers(role.getPermissionIds()))
        .sorted(Comparator.comparing(Role::getName))
        .map(
            role ->
                new RoleSummaryResponse(
                    role.getId(),
                    role.getName(),
                    Boolean.TRUE.equals(role.getSystemRole()),
                    role.getPermissionIds().size()))
        .toList();
  }

  @Transactional
  public AdminStaffResponse create(AdminStaffCreateRequest req, String actorEmail) {
    Actor actor = actor(actorEmail);
    Map<Long, Role> roles = requireRoles(req.getRoleIds());
    requireGrantable(roles.values(), actor);
    requireStoresWithinActorScope(req.getStoreScopeType(), req.getStoreIds(), actor.scope());
    if (repository.findByEmail(req.getEmail().toLowerCase(Locale.ROOT)).isPresent()) {
      throw new DuplicateStaffEmailException("このメールアドレスは既に登録されています");
    }
    User user =
        User.builder()
            .email(req.getEmail())
            .password(passwordEncoder.encode(req.getPassword()))
            .displayName(req.getDisplayName())
            .enabled(true)
            .userType(UserType.STAFF)
            .roleIds(req.getRoleIds())
            .storeScopeType(req.getStoreScopeType())
            .storeIds(req.getStoreIds())
            .build();
    user = save(user);
    businessAudit.recordCurrent(
        null,
        "STAFF_CREATED",
        "USER_GRANTS",
        user.getId().toString(),
        Map.of(),
        BusinessAudit.grants(user));
    return toResponse(user, roles, actor);
  }

  /** 授権（ロール×店舗集合）だけを更新する。停止・再開は専用の操作で、この面は enabled を受け取らない。 */
  @Transactional
  public AdminStaffResponse update(Long id, AdminStaffUpdateRequest req, String actorEmail) {
    Actor actor = actor(actorEmail);
    User user = requireVisible(id, actor);
    // 陳腐化した編集フォームの提出は JPA の @Version では捕まらない（再読込後の正当な更新に見える）
    // ため、応答で往復させた version を明示比対して 409 で拒否する。
    if (!user.getVersion().equals(req.getVersion())) {
      throw new StaleStaffUpdateException("他の担当者が更新しました。最新の内容を確認してください");
    }
    requireEditable(user, actor);
    Map<Long, Role> roles = requireRoles(req.getRoleIds());
    requireGrantable(roles.values(), actor);
    requireStoresWithinActorScope(req.getStoreScopeType(), req.getStoreIds(), actor.scope());
    // 供給ロールの追加は並行しうるため、ロック前は要求に既存ロールの除去があるかだけで判断する。
    if (user.getEnabled() && !req.getRoleIds().containsAll(user.getRoleIds())) {
      roleManageHolderGuard.requireAfterGrantChange(user, req.getRoleIds());
    }
    var before = BusinessAudit.grants(user);
    credentialOperations.reassignGrants(
        user, req.getRoleIds(), req.getStoreScopeType(), req.getStoreIds());
    user = save(user);
    if (!before.equals(BusinessAudit.grants(user)))
      businessAudit.recordCurrent(
          null,
          "STAFF_GRANTS_CHANGED",
          "USER_GRANTS",
          user.getId().toString(),
          before,
          BusinessAudit.grants(user));
    return toResponse(user, roles, actor);
  }

  /**
   * 停止する。既に停止済みでも 204 で受理し、失効イベントだけは必ず発行する — AFTER_COMMIT の Redis 書込みが失敗したとき、同じ要求の再送で失効を書き直せるようにする
   * （差分語義だと再送では何も起きず、再開してから止め直す以外に復旧手段が無くなる）。
   */
  @Transactional
  public void suspend(Long id, String actorEmail) {
    Actor actor = actor(actorEmail);
    User visible = requireVisible(id, actor);
    // 自分自身を停止すると自らのセッションも即時失効し、以後の操作ができなくなる（サポート経路がない自己ロックアウト）ため拒否する。
    if (visible.getEmail().equals(actorEmail)) {
      throw new SelfStopNotAllowedException("自分自身を停止することはできません");
    }
    requireEditable(visible, actor);
    User target = roleManageHolderGuard.loadForSuspension(id);
    boolean wasEnabled = target.getEnabled();
    credentialOperations.stop(target);
    if (wasEnabled) {
      repository.saveAndFlush(target);
      businessAudit.record(
          actorEmail,
          null,
          "STAFF_ACCOUNT_SUSPENDED",
          "USER_GRANTS",
          id.toString(),
          null,
          null,
          Map.of("enabled", "true"),
          Map.of("enabled", "false"));
    }
  }

  /** 再開する。既に有効でも 204 で受理する（冪等）。停止で増えた版は戻らないため、停止前のセッションは復活せず再ログインを要する。 */
  @Transactional
  public void resume(Long id, String actorEmail) {
    Actor actor = actor(actorEmail);
    User target = requireVisible(id, actor);
    requireEditable(target, actor);
    if (!target.getEnabled()) {
      target.resume();
      repository.saveAndFlush(target);
      businessAudit.recordCurrent(
          null,
          "STAFF_ACCOUNT_RESUMED",
          "USER_GRANTS",
          id.toString(),
          Map.of("enabled", "false"),
          Map.of("enabled", "true"));
    }
  }

  /**
   * 仮パスワードを発行して再設定し、対象の既存セッションを失効させる。生値の戻り値はこの一度だけで、以後どこからも取り出せない。
   *
   * <p>直列化点は押さえない。再設定は enabled もロール構成も動かさないので ROLE_MANAGE 保持者の母集団を減らしようがない。行ロックも取らない —
   * 状態機械の遷移が無く、並行書込みは楽観ロック（@Version）が担う。
   */
  @Transactional
  public String resetPassword(Long id, String actorEmail) {
    Actor actor = actor(actorEmail);
    User target = requireVisible(id, actor);
    // 自己再設定は名指しで拒む。JWT の権限は発行時の写しで降格後も失効まで残るため、降格済みの残存セッションが
    // 委譲守衛を素通りして口座を恒久奪取できてしまう。
    if (target.getEmail().equals(actorEmail)) {
      throw new SelfPasswordResetNotAllowedException("自分自身のパスワードは再設定できません");
    }
    if (holdsAny(
        target.getRoleIds(),
        roleRepository.findIdsByPermissionCode(PermissionCode.ROLE_MANAGE.name()))) {
      throw new HqPasswordResetNotAllowedException("管理者のパスワードは再設定できません");
    }
    requireEditable(target, actor);
    String temporaryPassword = temporaryPassword();
    credentialOperations.changePassword(target, passwordEncoder.encode(temporaryPassword));
    repository.saveAndFlush(target);
    businessAudit.record(
        actorEmail,
        null,
        "STAFF_PASSWORD_RESET",
        "USER_GRANTS",
        id.toString(),
        null,
        null,
        Map.of(),
        Map.of("sessions_revoked", "true"));
    return temporaryPassword;
  }

  /** 仮パスワード（URL 安全 Base64 の 16 文字）。生値は符号化して保存した後、応答以外のどこにも残さない。 */
  private String temporaryPassword() {
    byte[] bytes = new byte[12];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** 行使者の権限並集と店舗集合。作用域を解決できない要求は fail-closed に拒む（店舗境界の唯一の担保がこれである以上、既定で通してはならない）。 */
  private Actor actor(String actorEmail) {
    StoreScope scope = storeScopeHolder.current();
    if (scope == null) {
      throw new AccessDeniedException("授権店舗集合を解決できません");
    }
    User user =
        repository
            .findByEmail(actorEmail)
            .orElseThrow(() -> new StaleSessionException("認証セッションの主体が存在しません"));
    Set<Long> permissionIds =
        roleRepository.findAllById(user.getRoleIds()).stream()
            .flatMap(role -> role.getPermissionIds().stream())
            .collect(Collectors.toUnmodifiableSet());
    boolean definesRoles =
        holdsAny(
            user.getRoleIds(),
            roleRepository.findIdsByPermissionCode(PermissionCode.ROLE_MANAGE.name()));
    return new Actor(user, permissionIds, definesRoles, scope);
  }

  /**
   * 対象行を取り出す。可視性の述語は一覧と同一で、id を直に指しても一覧に出ない行へは届かない — 本人種別がスタッフ以外の行と、行使者の
   * 店舗集合と交わらない行は、存在しても「見つからない」に倒す。対象外と不在を呼出側から区別させないため、両者は同一の応答になる。
   */
  private User requireVisible(Long id, Actor actor) {
    return repository
        .findById(id)
        .filter(user -> user.getUserType() == UserType.STAFF)
        .filter(user -> intersects(user, actor.scope()))
        .orElseThrow(() -> new NotFoundException("従業員が見つかりません: " + id));
  }

  private static boolean intersects(User user, StoreScope actorScope) {
    if (actorScope.allStores() || user.getStoreScopeType() == StoreScopeType.ALL_STORES) {
      return true;
    }
    return user.getStoreIds().stream().anyMatch(actorScope.storeIds()::contains);
  }

  private void requireEditable(User user, Actor actor) {
    if (!editableBy(user, actor)) {
      throw new StaffOutOfDelegationScopeException("このアカウントを操作する権限がありません");
    }
  }

  /** 対象の権限並集 ⊆ 行使者の権限並集（ROLE_MANAGE 保持者は免除）かつ 対象の店舗集合 ⊆ 行使者の店舗集合。 */
  private boolean editableBy(User user, Actor actor) {
    return editableBy(user, rolesOf(user.getRoleIds()), actor);
  }

  private static boolean editableBy(User user, Map<Long, Role> roles, Actor actor) {
    boolean permissionsWithin =
        user.getRoleIds().stream()
            .map(roles::get)
            .filter(role -> role != null)
            .allMatch(role -> actor.covers(role.getPermissionIds()));
    return permissionsWithin
        && withinScope(user.getStoreScopeType(), user.getStoreIds(), actor.scope());
  }

  /** 付与するロールの権限並集が行使者の権限並集に収まること。ロールを定義できる者以外は、自身が持たない権限を配れない。 */
  private static void requireGrantable(Collection<Role> roles, Actor actor) {
    for (Role role : roles) {
      if (!actor.covers(role.getPermissionIds())) {
        throw new InvalidRoleGrantException("自分が持たない権限を含むロールは付与できません");
      }
    }
  }

  /** 付与・変更しようとしている店舗集合が行使者の店舗集合に収まること。検証は付与・変更時のみで、既往の付与へは遡らない。 */
  private static void requireStoresWithinActorScope(
      StoreScopeType scopeType, Set<Long> storeIds, StoreScope actorScope) {
    if (!withinScope(scopeType, storeIds, actorScope)) {
      throw new InvalidStoreScopeException("担当店舗の範囲を超える店舗は指定できません");
    }
  }

  /** 店舗集合の包含判定。ALL_STORES の対象を扱えるのは ALL_STORES の行使者だけ（部分集合の自然な帰結）。 */
  private static boolean withinScope(
      StoreScopeType scopeType, Set<Long> storeIds, StoreScope actorScope) {
    if (actorScope.allStores()) {
      return true;
    }
    if (scopeType == StoreScopeType.ALL_STORES) {
      return false;
    }
    return storeIds == null || actorScope.storeIds().containsAll(storeIds);
  }

  private static boolean holdsAny(Set<Long> roleIds, Set<Long> targetRoleIds) {
    return roleIds.stream().anyMatch(targetRoleIds::contains);
  }

  /** 付与するロールを押さえ（{@link RoleRepository#lockIdsForGrant}）、全て実在することを検証して id→ロールの対応を返す（応答組立にも使う）。 */
  private Map<Long, Role> requireRoles(Set<Long> roleIds) {
    roleRepository.lockIdsForGrant(roleIds);
    Map<Long, Role> roles = rolesOf(roleIds);
    if (roles.size() != roleIds.size()) {
      throw new ServiceException("指定されたロールが存在しません");
    }
    return roles;
  }

  private Map<Long, Role> rolesOf(Set<Long> roleIds) {
    return roleRepository.findAllById(roleIds).stream()
        .collect(Collectors.toMap(Role::getId, role -> role));
  }

  /**
   * 保存時の整合性違反を制約名で分類する。email 一意制約違反（同一メール二重送信レース）は重複エラー、店舗 FK 違反（存在しない店舗 id）は店舗エラー、ロール FK
   * 違反（requireRoles 通過後の並行ロール削除）はロール不存在エラーへ変換する（いずれも 400）。それ以外の整合性違反は実装欠陥であり、握りつぶさず全域ハンドラの分類に委ねる。
   */
  private User save(User user) {
    return IntegrityViolations.translateOnFailure(
        () -> repository.saveAndFlush(user),
        Map.of(
            DbConstraint.UQ_T_USERS_EMAIL,
            () -> new DuplicateStaffEmailException("このメールアドレスは既に登録されています"),
            DbConstraint.FK_T_USER_STORES_STORE,
            () -> new InvalidStoreScopeException("指定された店舗が存在しません"),
            DbConstraint.FK_T_USER_ROLES_ROLE,
            () -> new ServiceException("指定されたロールが存在しません")));
  }

  private static AdminStaffResponse toResponse(User user, Map<Long, Role> roles, Actor actor) {
    List<AdminStaffResponse.RoleRef> refs =
        user.getRoleIds().stream()
            .map(
                id ->
                    new AdminStaffResponse.RoleRef(
                        id, roles.containsKey(id) ? roles.get(id).getName() : null))
            .sorted(
                Comparator.comparing(
                    AdminStaffResponse.RoleRef::name,
                    Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    return new AdminStaffResponse(
        user.getId(),
        user.getEmail(),
        user.getDisplayName(),
        user.getEnabled(),
        refs,
        user.getStoreScopeType(),
        new HashSet<>(user.getStoreIds()),
        user.getVersion(),
        editableBy(user, roles, actor));
  }
}
