package com.kizuna.user.application;

import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.user.api.dto.RoleSummaryResponse;
import com.kizuna.user.api.dto.ServiceIdentityCreateRequest;
import com.kizuna.user.api.dto.ServiceIdentityResponse;
import com.kizuna.user.api.dto.ServiceIdentityRoleRef;
import com.kizuna.user.api.dto.ServiceIdentitySummaryResponse;
import com.kizuna.user.api.dto.ServiceIdentityUpdateRequest;
import com.kizuna.user.domain.InvalidRoleGrantException;
import com.kizuna.user.domain.InvalidStoreScopeException;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.Role;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StaleServiceIdentityUpdateException;
import com.kizuna.user.domain.UserType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.EscapeCharacter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * サービスID（本人種別 SERVICE）の管理ユースケース。作成・一覧・授権変更・停止・再開を一つの面で扱う —
 * 作成が授権（ロール×店舗集合）を伴うため、授権を一切動かさないアカウント管理とは面ごと分ける（ADR 0025）。
 *
 * <p>付与できるのは自作ロールに限る。PlatformUser はロール id しか持たず is_system を参照できない（跨集約）ため、 実体不変条件でなくこの授与口で拒む。
 */
@Service
@RequiredArgsConstructor
public class ServiceIdentityService {

  /** LIKE パターンのエスケープ規則。派生クエリが内部で使うものと同一で、手書きの cb.like にも同じ規則を適用する。 */
  private static final EscapeCharacter LIKE_ESCAPE = EscapeCharacter.DEFAULT;

  private final PlatformUserRepository repository;
  private final RoleRepository roleRepository;

  @Transactional(readOnly = true)
  public Page<ServiceIdentitySummaryResponse> list(String search, Pageable pageable) {
    Page<PlatformUser> identities = repository.findAll(identitySpec(search), pageable);
    Map<Long, String> roleNames =
        roleNamesOf(
            identities.getContent().stream()
                .flatMap(user -> user.getRoleIds().stream())
                .collect(Collectors.toSet()));
    return identities.map(
        user ->
            new ServiceIdentitySummaryResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEnabled(),
                rolesOf(user, roleNames),
                user.getStoreScopeType(),
                new HashSet<>(user.getStoreIds())));
  }

  /**
   * 対象は本人種別 SERVICE の全行。検索語は表示名（用途名）への部分一致 — サービスIDは email を持たないため検索軸は表示名だけ。
   *
   * <p>null の条件は述語を生成しない（JPQL の ":param is null or ..." パターンは PostgreSQL の null パラメータ型推論で 500 になるため
   * Specification で組み立てる）。
   */
  private static Specification<PlatformUser> identitySpec(String search) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("userType"), UserType.SERVICE));
      if (search != null) {
        char escape = LIKE_ESCAPE.getEscapeCharacter();
        String pattern = "%" + LIKE_ESCAPE.escape(search.toLowerCase(Locale.ROOT)) + "%";
        predicates.add(cb.like(cb.lower(root.get("displayName")), pattern, escape));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  /** 1 件取得。編集中に競合（409）が起きたとき、一覧の現在ページに対象が居なくても最新の版を取り直せるようにするための経路。 */
  @Transactional(readOnly = true)
  public ServiceIdentityResponse get(Long id) {
    PlatformUser user = requireServiceIdentity(id);
    return toResponse(user, roleNamesOf(user.getRoleIds()));
  }

  @Transactional
  public ServiceIdentityResponse create(ServiceIdentityCreateRequest req) {
    Map<Long, String> roleNames = requireCustomRoles(req.getRoleIds());
    PlatformUser user =
        PlatformUser.builder()
            .displayName(req.getDisplayName())
            .enabled(true)
            .userType(UserType.SERVICE)
            .roleIds(req.getRoleIds())
            .storeScopeType(req.getStoreScopeType())
            .storeIds(req.getStoreIds())
            .build();
    return toResponse(save(user), roleNames);
  }

  /** 授権（ロール×店舗集合）だけを更新する。停止・再開は専用端点の領分で、この面は enabled を受け取らない。 */
  @Transactional
  public ServiceIdentityResponse update(Long id, ServiceIdentityUpdateRequest req) {
    Map<Long, String> roleNames = requireCustomRoles(req.getRoleIds());
    PlatformUser user = requireServiceIdentity(id);
    // 陳腐化した編集フォームの提出は JPA の @Version では捕まらない（再読込後の正当な更新に見える）
    // ため、応答で往復させた version を明示比対して 409 で拒否する。
    if (!user.getVersion().equals(req.getVersion())) {
      throw new StaleServiceIdentityUpdateException("他の管理者が更新しました。最新の内容を確認してください");
    }
    user.reassignGrants(req.getRoleIds(), req.getStoreScopeType(), req.getStoreIds());
    return toResponse(save(user), roleNames);
  }

  /**
   * 停止する。既に停止済みでも 204 で受理する（冪等）。スタッフ停止と異なり権限目録の直列化点・失効イベント・自己停止検査を持たない —
   * サービスIDは対話ログインできず、失効させるセッションが無く、最後の管理権限保持者の母集団（ログインできる STAFF）にも入らない。
   */
  @Transactional
  public void suspend(Long id) {
    PlatformUser user = requireServiceIdentityForUpdate(id);
    if (user.getEnabled()) {
      user.stop();
      repository.saveAndFlush(user);
    }
  }

  /** 再開する。既に有効でも 204 で受理する（冪等）。 */
  @Transactional
  public void resume(Long id) {
    PlatformUser user = requireServiceIdentityForUpdate(id);
    if (!user.getEnabled()) {
      user.resume();
      repository.saveAndFlush(user);
    }
  }

  /** 行使者が付与できるロール（自作ロール）の目録。付与可否の述語をサーバ側の単源に置き、前端に判定を複製させないための読み口である。 */
  @Transactional(readOnly = true)
  public List<RoleSummaryResponse> grantableRoles() {
    return roleRepository.findAllSummaries().stream()
        .filter(summary -> !Boolean.TRUE.equals(summary.getSystemRole()))
        .map(
            summary ->
                new RoleSummaryResponse(
                    summary.getId(), summary.getName(), false, summary.getPermissionCount()))
        .toList();
  }

  /** サービスID管理の対象行を取り出す。本人種別が SERVICE 以外の行は、存在しても対象外として「見つからない」に倒す — 人のアカウントの在否をこの面から列挙させない。 */
  private PlatformUser requireServiceIdentity(Long id) {
    return requireService(repository.findById(id), id);
  }

  /**
   * 停止・再開用に行を押さえて取り出す。冪等 204 の約束を並行再送でも守るための行ロック — 素の読みだと両方が enabled を読んだ後に遅い側が版競合で 409
   * に化ける。事前検査で実体を読まずに最初から押さえる（読み後の昇格は版照合を伴う）。
   */
  private PlatformUser requireServiceIdentityForUpdate(Long id) {
    return requireService(repository.findByIdForUpdate(id), id);
  }

  private static PlatformUser requireService(Optional<PlatformUser> row, Long id) {
    return row.filter(user -> user.getUserType() == UserType.SERVICE)
        .orElseThrow(() -> new NotFoundException("サービスIDが見つかりません: " + id));
  }

  /** 指定 id のロールが全て実在する自作ロールであることを検証し、id→名称の対応を返す（応答組立にも使う）。 */
  private Map<Long, String> requireCustomRoles(Set<Long> roleIds) {
    List<Role> roles = roleRepository.findAllById(roleIds);
    if (roles.size() != roleIds.size()) {
      throw new ServiceException("指定されたロールが存在しません");
    }
    if (roles.stream().anyMatch(role -> Boolean.TRUE.equals(role.getSystemRole()))) {
      throw new InvalidRoleGrantException("サービスIDにプラットフォーム既定ロールは付与できません");
    }
    return roles.stream().collect(Collectors.toMap(Role::getId, Role::getName));
  }

  /**
   * 保存時の整合性違反を制約名で分類する。店舗 FK 違反（存在しない店舗 id）は店舗エラー、ロール FK 違反（requireCustomRoles 通過後の並行ロール削除）は
   * ロール不存在エラーへ変換する（いずれも 400）。それ以外の整合性違反は実装欠陥であり、握りつぶさず全域ハンドラの分類に委ねる。
   */
  private PlatformUser save(PlatformUser user) {
    return IntegrityMappedSaves.save(
        repository,
        user,
        Map.of(
            DbConstraint.FK_T_USER_STORES_STORE,
            () -> new InvalidStoreScopeException("指定された店舗が存在しません"),
            DbConstraint.FK_T_USER_ROLES_ROLE,
            () -> new ServiceException("指定されたロールが存在しません")));
  }

  private static ServiceIdentityResponse toResponse(
      PlatformUser user, Map<Long, String> roleNames) {
    return new ServiceIdentityResponse(
        user.getId(),
        user.getDisplayName(),
        user.getEnabled(),
        rolesOf(user, roleNames),
        user.getStoreScopeType(),
        new HashSet<>(user.getStoreIds()),
        user.getVersion());
  }

  private static List<ServiceIdentityRoleRef> rolesOf(
      PlatformUser user, Map<Long, String> roleNames) {
    return user.getRoleIds().stream()
        .map(id -> new ServiceIdentityRoleRef(id, roleNames.get(id)))
        .sorted(
            Comparator.comparing(
                ServiceIdentityRoleRef::name, Comparator.nullsLast(Comparator.naturalOrder())))
        .toList();
  }

  private Map<Long, String> roleNamesOf(Set<Long> roleIds) {
    return roleRepository.findAllById(roleIds).stream()
        .collect(Collectors.toMap(Role::getId, Role::getName));
  }
}
