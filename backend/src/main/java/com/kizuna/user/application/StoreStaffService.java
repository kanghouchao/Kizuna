package com.kizuna.user.application;

import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.user.api.dto.RoleSummaryResponse;
import com.kizuna.user.api.dto.StoreStaffCreateRequest;
import com.kizuna.user.api.dto.StoreStaffResponse;
import com.kizuna.user.api.dto.StoreStaffUpdateRequest;
import com.kizuna.user.domain.DuplicateStaffEmailException;
import com.kizuna.user.domain.InvalidRoleGrantException;
import com.kizuna.user.domain.InvalidStoreScopeException;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.PlatformUserResumed;
import com.kizuna.user.domain.PlatformUserStopped;
import com.kizuna.user.domain.Role;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StaffOutOfDelegationScopeException;
import com.kizuna.user.domain.StaleStaffUpdateException;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 店舗スタッフの授権管理ユースケース。対象は本人種別 STAFF のうち店舗側ロールしか持たない者に限る — HQ 側ロールを 1 つでも持つ者は管理者管理（ROLE_MANAGE
 * 門）が扱い、この面には在否すら出さない（ADR 0020）。
 *
 * <p>行使者の事実（ROLE_MANAGE の保持と担当店舗集合）は JWT から、対象の現況は DB から取る。PlatformUser は StoreScopedEntity ではないため
 * storeFilter / @StoreSetScoped（ADR 0002）の機構は乗らず、店舗境界は本クラスの明示検証が唯一の担保である。
 *
 * <p>防提権守衛（ADR 0020）: G1 付与できるのは店舗側ロールかつ委譲権限（STAFF_MANAGE）を含まないもの、G2 対象の店舗集合 ⊆ 行使者の担当店舗集合、G3
 * 編集・停止できるのは委譲権限の非実効保持者かつ現在の店舗集合が行使者の集合に収まる者。いずれも ROLE_MANAGE 保持者には課さない。
 *
 * <p>不減零（G5）はここでは検査しない。ROLE_MANAGE は Console.PLATFORM の権限なので、それを含むロールは定義上 HQ 側ロールであり、
 * 母集団からも付与可能集合からも外れている — この面を通って ROLE_MANAGE 実効保持者が減ることは構造的に起こらない。
 */
@Service
@RequiredArgsConstructor
public class StoreStaffService {

  /** LIKE パターンのエスケープ規則。派生クエリが内部で使うものと同一で、手書きの cb.like にも同じ規則を適用する。 */
  private static final EscapeCharacter LIKE_ESCAPE = EscapeCharacter.DEFAULT;

  /** HQ 側ロールの判定に使う権限コード（Console.PLATFORM の全権限）。目録は静的なので毎回引き直さない。 */
  private static final Set<String> PLATFORM_PERMISSION_CODES =
      Arrays.stream(PermissionCode.values())
          .filter(code -> code.getConsole() == PermissionCode.Console.PLATFORM)
          .map(PermissionCode::name)
          .collect(Collectors.toUnmodifiableSet());

  private final PlatformUserRepository repository;
  private final RoleRepository roleRepository;
  private final PasswordEncoder passwordEncoder;
  private final ApplicationEventPublisher eventPublisher;
  private final StoreContext storeContext;

  @Transactional(readOnly = true)
  public Page<StoreStaffResponse> list(String search, Pageable pageable) {
    Set<Long> hqRoleIds = hqRoleIds();
    Page<PlatformUser> staff =
        repository.findAll(staffSpec(search, storeContext.getStoreId(), hqRoleIds), pageable);
    Map<Long, String> roleNames =
        roleNamesOf(
            staff.getContent().stream()
                .flatMap(user -> user.getRoleIds().stream())
                .collect(Collectors.toSet()));
    Set<Long> delegationRoleIds = delegationRoleIds();
    StoreScope scope = actorScope();
    return staff.map(user -> toResponse(user, roleNames, delegationRoleIds, scope));
  }

  /**
   * 一覧の対象は本人種別 STAFF かつ HQ 側ロールを 1 つも持たない行のうち、店舗コンテキストの店を担当範囲に含むものに限る。ALL_STORES は個別 id
   * を持たないまま全店舗を覆うため常に該当させる。検索語は表示名とメールアドレスを横断する部分一致。
   *
   * <p>null の条件は述語を生成しない（JPQL の ":param is null or ..." パターンは PostgreSQL の null パラメータ型推論で 500 になるため
   * Specification で組み立てる）。
   */
  private static Specification<PlatformUser> staffSpec(
      String search, Long storeId, Set<Long> hqRoleIds) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("userType"), UserType.STAFF));
      if (!hqRoleIds.isEmpty()) {
        // ロール集合は @ElementCollection のため member of（相関 exists）で組み、親行を結合で増やさない。
        // 管理者管理の絞りの補集合であり、HQ 側ロールを 1 つでも持てばこの面には現れない（列挙防止）。
        predicates.add(
            cb.not(
                cb.or(
                    hqRoleIds.stream()
                        .map(roleId -> cb.isMember(roleId, root.<Set<Long>>get("roleIds")))
                        .toArray(Predicate[]::new))));
      }
      predicates.add(
          cb.or(
              cb.equal(root.get("storeScopeType"), StoreScopeType.ALL_STORES),
              cb.isMember(storeId, root.get("storeIds"))));
      if (search != null) {
        char escape = LIKE_ESCAPE.getEscapeCharacter();
        String pattern = "%" + LIKE_ESCAPE.escape(search.toLowerCase(Locale.ROOT)) + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("displayName")), pattern, escape),
                cb.like(cb.lower(root.get("email")), pattern, escape)));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  /** 1 件取得。編集中に競合（409）が起きたとき、一覧の現在ページに対象が居なくても最新の版を取り直せるようにするための経路。 */
  @Transactional(readOnly = true)
  public StoreStaffResponse get(Long id) {
    PlatformUser user = requireManagedStaff(id, hqRoleIds());
    return toResponse(user, roleNamesOf(user.getRoleIds()), delegationRoleIds(), actorScope());
  }

  @Transactional
  public StoreStaffResponse create(StoreStaffCreateRequest req) {
    Set<Long> delegationRoleIds = delegationRoleIds();
    StoreScope scope = actorScope();
    requireGrantableRoles(req.getRoleIds(), hqRoleIds(), delegationRoleIds);
    requireStoresWithinActorScope(req.getStoreScopeType(), req.getStoreIds(), scope);
    Map<Long, String> roleNames = requireRoles(req.getRoleIds());
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
    return toResponse(save(user), roleNames, delegationRoleIds, scope);
  }

  @Transactional
  public StoreStaffResponse update(Long id, StoreStaffUpdateRequest req) {
    Set<Long> hqRoleIds = hqRoleIds();
    PlatformUser user = requireManagedStaff(id, hqRoleIds);
    Set<Long> delegationRoleIds = delegationRoleIds();
    StoreScope scope = actorScope();
    if (!editableBy(user, delegationRoleIds, scope)) {
      throw new StaffOutOfDelegationScopeException("このアカウントを編集する権限がありません");
    }
    // 陳腐化した編集フォームの提出は JPA の @Version では捕まらない（再読込後の正当な更新に見える）
    // ため、応答で往復させた version を明示比対して 409 で拒否する。
    if (!user.getVersion().equals(req.getVersion())) {
      throw new StaleStaffUpdateException("他の担当者が更新しました。最新の内容を確認してください");
    }
    requireGrantableRoles(req.getRoleIds(), hqRoleIds, delegationRoleIds);
    requireStoresWithinActorScope(req.getStoreScopeType(), req.getStoreIds(), scope);
    Map<Long, String> roleNames = requireRoles(req.getRoleIds());
    user.reassignGrants(req.getRoleIds(), req.getStoreScopeType(), req.getStoreIds());
    // enabled の遷移（null=現状維持）。停止は行を残し、過去の実行主体の記録を保持する。
    if (Boolean.FALSE.equals(req.getEnabled()) && user.getEnabled()) {
      user.stop();
    }
    if (Boolean.TRUE.equals(req.getEnabled()) && !user.getEnabled()) {
      user.resume();
    }
    // 失効の即時反映は「本リクエストが停止/再開を明示的に要求したか」で判定する（現在状態との差分ではない）。
    // 差分語義だと、AFTER_COMMIT の Redis 書き込みが失敗して 500 になった後の再送でイベントが
    // 発行されず、resume→stop 以外に復旧手段が無くなる。
    if (Boolean.FALSE.equals(req.getEnabled())) {
      eventPublisher.publishEvent(new PlatformUserStopped(user.getEmail()));
    }
    if (Boolean.TRUE.equals(req.getEnabled())) {
      eventPublisher.publishEvent(new PlatformUserResumed(user.getEmail()));
    }
    return toResponse(save(user), roleNames, delegationRoleIds, scope);
  }

  /** 行使者が付与できるロールの目録。防提権述語（店舗側ロールであること・委譲権限を含まないこと）をサーバ側の単源に置き、 前端に判定を複製させないための読み口である。 */
  @Transactional(readOnly = true)
  public List<RoleSummaryResponse> grantableRoles() {
    Set<Long> excluded = new HashSet<>(hqRoleIds());
    excluded.addAll(delegationRoleIds());
    return roleRepository.findAllSummaries().stream()
        .filter(summary -> !excluded.contains(summary.getId()))
        .map(
            summary ->
                new RoleSummaryResponse(
                    summary.getId(),
                    summary.getName(),
                    Boolean.TRUE.equals(summary.getSystemRole()),
                    summary.getPermissionCount()))
        .toList();
  }

  /**
   * 店舗スタッフ管理の対象行を取り出す。可視性の述語は一覧と同一で、id を直に指しても一覧に出ない行へは届かない — 本人種別がスタッフ以外（CAST/MEMBER）の行、HQ 側ロールを 1
   * つでも持つ行、店舗文脈の店を担当範囲に含まない行は、 存在しても「見つからない」に倒す。対象外と不在を呼出側から区別させないため、両者は同一の応答になる。
   *
   * <p>店舗文脈で絞るのは可視性の軸であり、付与できる店舗集合の軸（G2）とは別物である。後者は行使者の担当店舗集合 <b>全体</b>で判定し、文脈の単店には縮めない —
   * 対象の集合が行使者の別の担当店に跨るのは正常だからである。
   */
  private PlatformUser requireManagedStaff(Long id, Set<Long> hqRoleIds) {
    Long contextStoreId = storeContext.getStoreId();
    return repository
        .findById(id)
        .filter(user -> user.getUserType() == UserType.STAFF)
        .filter(user -> !holdsAny(user.getRoleIds(), hqRoleIds))
        .filter(user -> user.authorizes(contextStoreId))
        .orElseThrow(() -> new NotFoundException("スタッフが見つかりません: " + id));
  }

  /** HQ 側ロール（Console.PLATFORM の権限を 1 つ以上含むロール）の id 集合。 */
  private Set<Long> hqRoleIds() {
    return roleRepository.findIdsByPermissionCodeIn(PLATFORM_PERMISSION_CODES);
  }

  /**
   * 委譲権限（STAFF_MANAGE）を含むロールの id 集合。ROLE_MANAGE 保持者には守衛が課されないので、その場合は引かない （空集合は「委譲権限を含むロールが 1
   * つも無い」と同じ効きになる）。
   */
  private Set<Long> delegationRoleIds() {
    return actorHasRoleManage()
        ? Set.of()
        : roleRepository.findIdsByPermissionCode(PermissionCode.STAFF_MANAGE.name());
  }

  /**
   * G1: 付与できるのは店舗側ロールで、かつ委譲権限を含まないものに限る。HQ 側ロールの排除は ROLE_MANAGE 保持者にも課す —
   * 素通しにすると付与した直後にこの面から消えるアカウントが作れてしまい、店舗側からは二度と辿り着けなくなる（管理者管理の領分）。
   */
  private static void requireGrantableRoles(
      Set<Long> roleIds, Set<Long> hqRoleIds, Set<Long> delegationRoleIds) {
    if (holdsAny(roleIds, hqRoleIds)) {
      throw new InvalidRoleGrantException("店舗スタッフにプラットフォーム権限を含むロールは付与できません");
    }
    if (holdsAny(roleIds, delegationRoleIds)) {
      throw new InvalidRoleGrantException("スタッフ管理権限を含むロールを付与する権限がありません");
    }
  }

  /** G2: 付与・変更しようとしている店舗集合が行使者の担当店舗集合に収まること。検証は付与・変更時のみで、既往の付与へは遡らない。 */
  private static void requireStoresWithinActorScope(
      StoreScopeType scopeType, Set<Long> storeIds, StoreScope actorScope) {
    if (!withinScope(scopeType, storeIds, actorScope)) {
      throw new InvalidStoreScopeException("担当店舗の範囲を超える店舗は指定できません");
    }
  }

  /**
   * G3: 編集・停止できる対象か。委譲権限の実効保持者（店長等）は同僚として一覧には出すが、停止は事実上の解任なので触らせない。 現在の店舗集合が行使者の集合に収まることも要求する —
   * さもないと、担当外の店を含むアカウントは「その店を落とす」形でしか 保存できず、善意の編集が他店の授権を壊す。
   */
  private static boolean editableBy(
      PlatformUser user, Set<Long> delegationRoleIds, StoreScope actorScope) {
    if (holdsAny(user.getRoleIds(), delegationRoleIds)) {
      return false;
    }
    return withinScope(user.getStoreScopeType(), user.getStoreIds(), actorScope);
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

  /** 行使者の担当店舗集合。解決できない要求は fail-closed に拒む（店舗境界の唯一の担保がこれである以上、既定で通してはならない）。 */
  private static StoreScope actorScope() {
    StoreScope scope =
        StoreScope.fromAuthentication(SecurityContextHolder.getContext().getAuthentication());
    if (scope == null) {
      throw new AccessDeniedException("授権店舗集合を解決できません");
    }
    return scope;
  }

  /** 行使者が Owner 層（ROLE_MANAGE）か。保持者には防提権守衛を課さない。 */
  private static boolean actorHasRoleManage() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return false;
    }
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch(PermissionCode.ROLE_MANAGE.authority()::equals);
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
   * 違反（requireRoles 通過後の並行ロール削除）はロール不存在エラーへ変換する（いずれも 400）。
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

  private static StoreStaffResponse toResponse(
      PlatformUser user,
      Map<Long, String> roleNames,
      Set<Long> delegationRoleIds,
      StoreScope actorScope) {
    List<StoreStaffResponse.RoleRef> roles =
        user.getRoleIds().stream()
            .map(id -> new StoreStaffResponse.RoleRef(id, roleNames.get(id)))
            .sorted(
                Comparator.comparing(
                    StoreStaffResponse.RoleRef::name,
                    Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    return new StoreStaffResponse(
        user.getId(),
        user.getEmail(),
        user.getDisplayName(),
        user.getEnabled(),
        roles,
        user.getStoreScopeType(),
        new HashSet<>(user.getStoreIds()),
        user.getVersion(),
        editableBy(user, delegationRoleIds, actorScope));
  }
}
