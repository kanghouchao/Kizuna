package com.kizuna.user.application;

import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.user.api.dto.StoreManagerAppointRequest;
import com.kizuna.user.api.dto.StoreManagerCandidateResponse;
import com.kizuna.user.api.dto.StoreManagerResponse;
import com.kizuna.user.domain.DuplicateStaffEmailException;
import com.kizuna.user.domain.InvalidStoreScopeException;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.Role;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.SystemRole;
import com.kizuna.user.domain.UserType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.EscapeCharacter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 店長設定のユースケース（Owner 層 = ROLE_MANAGE の領分）。店長は「STORE_MANAGER ロールを保持し、かつ当該店舗を担当範囲に含む者」として導出する — 独立した
 * 任命記録は持たない。
 *
 * <p>任命はロールの付与と担当店舗の追加、解任は担当店舗の除去である。ロール × 店舗集合は外積なので（ADR 0020）、任命は本人の担当店舗
 * <b>全体</b>を店長化し、解任は当該店舗の授権ごと落とす。
 *
 * <p>不変条件と衝突する解任（最後の 1 店・ALL_STORES）は自動降格せず 400 で撥ね、店舗スタッフ管理での明示操作へ誘導する。
 * 撥ねる側に倒すのは、ここで黙ってロールを剥がすと本人の他店での職位まで消えるためである。
 *
 * <p>不減零（ADR 0020 の守衛 G5）はこの面では検査しない。授与するのは STORE_MANAGER 一択で、既定ロールの権限構成はコード側が正本のまま API から
 * 改廃できない（{@link SystemRole}）ため、この面が ROLE_MANAGE を配ることも奪うこともない。解任も担当店舗集合しか触らない。
 */
@Service
@RequiredArgsConstructor
public class StoreManagerService {

  /** LIKE パターンのエスケープ規則。派生クエリが内部で使うものと同一で、手書きの cb.like にも同じ規則を適用する。 */
  private static final EscapeCharacter LIKE_ESCAPE = EscapeCharacter.DEFAULT;

  /** 一覧・候補の並び。offset ページングの境界を確定させるため、表示名には一意な副キーを添える。 */
  private static final Sort BY_DISPLAY_NAME = Sort.by("displayName", "id");

  private final PlatformUserRepository repository;
  private final RoleRepository roleRepository;
  private final PasswordEncoder passwordEncoder;
  private final StoreExistenceCheck storeExistenceCheck;

  /**
   * この店舗の店長一覧。ALL_STORES の保持者も全店舗を担当範囲に含むので該当する。
   *
   * <p>裸の {@code List} で返すのは、1 店舗の店長が業務上ひと握りに収まるためである（外積の帰結として複数人あり得るが、 「この店を担当する STORE_MANAGER
   * 保持者」という母集団自体が店舗ごとに有界）。
   */
  @Transactional(readOnly = true)
  public List<StoreManagerResponse> list(Long storeId) {
    requireStore(storeId);
    return repository
        .findAll(managerSpec(storeId, requireStoreManagerRole().getId()), BY_DISPLAY_NAME)
        .stream()
        .map(StoreManagerService::toResponse)
        .toList();
  }

  /**
   * 任命できる既存アカウントの候補。母集団は「有効な STAFF で、店舗側ロールしか持たず、担当店舗を個別指定していて、まだこの店舗の店長でない」者。
   *
   * <p>HQ 側ロール保持者を外すのは ADR 0020 の母集団分割（管理者管理の領分）による。ALL_STORES を外すのは、任命しても解任できない 店長ができてしまうためである —
   * 空の店舗集合から当該店舗を除去する形が存在しない。
   */
  @Transactional(readOnly = true)
  public Page<StoreManagerCandidateResponse> candidates(
      Long storeId, String search, Pageable pageable) {
    requireStore(storeId);
    return repository
        .findAll(
            candidateSpec(
                storeId, requireStoreManagerRole().getId(), roleRepository.findHqRoleIds(), search),
            pageable)
        .map(
            user ->
                new StoreManagerCandidateResponse(
                    user.getId(), user.getEmail(), user.getDisplayName()));
  }

  /**
   * 店長に任命する。{@code user_id} を伴えば既存アカウントへの付与、伴わなければ新規作成しての任命（初代店長の冷起動）。
   *
   * <p>既存アカウントへの付与はロール集合・店舗集合の和で行う — ロールだけ・店舗だけを既に満たす中途の状態から、 欠けている側を足して店長へ揃える。
   */
  @Transactional
  public StoreManagerResponse appoint(Long storeId, StoreManagerAppointRequest req) {
    requireStore(storeId);
    Role managerRole = requireStoreManagerRole();
    boolean hasNewAccountFields =
        req.getEmail() != null || req.getPassword() != null || req.getDisplayName() != null;
    if (req.getUserId() != null) {
      if (hasNewAccountFields) {
        throw new ServiceException("既存アカウントの任命と新規作成は同時に指定できません");
      }
      return appointExisting(storeId, req.getUserId(), managerRole.getId());
    }
    if (!hasNewAccountFields) {
      throw new ServiceException("任命するアカウントを指定してください");
    }
    return createAndAppoint(storeId, req, managerRole.getId());
  }

  private StoreManagerResponse appointExisting(Long storeId, Long userId, Long managerRoleId) {
    // 母集団外と不在は同じ応答に倒す（この面から他人のアカウントの在否を引き当てさせない）。
    PlatformUser user =
        repository
            .findByIdForUpdate(userId)
            .orElseThrow(() -> new ServiceException("このアカウントは店長に任命できません"));
    if (user.getRoleIds().contains(managerRoleId) && user.authorizes(storeId)) {
      throw new ServiceException("このアカウントは既にこの店舗の店長です");
    }
    if (!isCandidate(user, roleRepository.findHqRoleIds())) {
      throw new ServiceException("このアカウントは店長に任命できません");
    }
    Set<Long> roleIds = new HashSet<>(user.getRoleIds());
    roleIds.add(managerRoleId);
    Set<Long> storeIds = new HashSet<>(user.getStoreIds());
    storeIds.add(storeId);
    user.reassignGrants(roleIds, StoreScopeType.SPECIFIC_STORES, storeIds);
    return toResponse(save(user));
  }

  private StoreManagerResponse createAndAppoint(
      Long storeId, StoreManagerAppointRequest req, Long managerRoleId) {
    if (isBlank(req.getEmail()) || isBlank(req.getPassword()) || isBlank(req.getDisplayName())) {
      throw new ServiceException("メールアドレス・パスワード・表示名はいずれも必須です");
    }
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
            .roleIds(Set.of(managerRoleId))
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(storeId))
            .build();
    return toResponse(save(user));
  }

  /**
   * 店長を解任する。担当店舗集合から当該店舗を除去するだけで、ロールには触らない — 外積のため、ここでロールを剥がすと 本人が他店で持つ店長職まで一緒に消える。
   *
   * <p>除去が不変条件と衝突する 2 例（唯一の担当店舗・ALL_STORES）は、代わりにロールを剥がす自動降格へ倒さず 400 で撥ねる。
   */
  @Transactional
  public void dismiss(Long storeId, Long userId) {
    requireStore(storeId);
    Long managerRoleId = requireStoreManagerRole().getId();
    PlatformUser user =
        repository
            .findByIdForUpdate(userId)
            .filter(target -> target.getUserType() == UserType.STAFF)
            .filter(target -> target.getRoleIds().contains(managerRoleId))
            .filter(target -> target.authorizes(storeId))
            .orElseThrow(() -> new NotFoundException("この店舗の店長が見つかりません: " + userId));
    if (user.getStoreScopeType() == StoreScopeType.ALL_STORES) {
      throw new InvalidStoreScopeException("全店舗を担当しているため、この画面からは解任できません。店舗スタッフ管理で担当店舗を指定してください");
    }
    if (user.getStoreIds().size() == 1) {
      throw new InvalidStoreScopeException("最後の担当店舗のため解任できません。店舗スタッフ管理でロールの付け替えまたは停止を行ってください");
    }
    Set<Long> storeIds = new HashSet<>(user.getStoreIds());
    storeIds.remove(storeId);
    user.reassignGrants(user.getRoleIds(), StoreScopeType.SPECIFIC_STORES, storeIds);
    save(user);
  }

  /** 店長の導出条件。任命記録を持たないので、ロールの保持と担当範囲の 2 条件がそのまま述語になる。 */
  private static Specification<PlatformUser> managerSpec(Long storeId, Long managerRoleId) {
    return (root, query, cb) ->
        cb.and(
            cb.equal(root.get("userType"), UserType.STAFF),
            cb.isMember(managerRoleId, root.<Set<Long>>get("roleIds")),
            cb.or(
                cb.equal(root.get("storeScopeType"), StoreScopeType.ALL_STORES),
                cb.isMember(storeId, root.get("storeIds"))));
  }

  /**
   * 候補の母集団。{@link #isCandidate} と同じ条件を HQL 側で表現したもので、任命の受け口は必ず後者でも検査する（画面が古くても通さない）。
   *
   * <p>null の条件は述語を生成しない（JPQL の ":param is null or ..." パターンは PostgreSQL の null パラメータ型推論で 500 になるため
   * Specification で組み立てる）。
   */
  private static Specification<PlatformUser> candidateSpec(
      Long storeId, Long managerRoleId, Set<Long> hqRoleIds, String search) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("userType"), UserType.STAFF));
      predicates.add(cb.isTrue(root.get("enabled")));
      predicates.add(cb.equal(root.get("storeScopeType"), StoreScopeType.SPECIFIC_STORES));
      if (!hqRoleIds.isEmpty()) {
        // ロール集合は @ElementCollection のため member of（相関 exists）で組み、親行を結合で増やさない。
        predicates.add(
            cb.not(
                cb.or(
                    hqRoleIds.stream()
                        .map(roleId -> cb.isMember(roleId, root.<Set<Long>>get("roleIds")))
                        .toArray(Predicate[]::new))));
      }
      // 既にこの店舗の店長である者だけを外す。他店の店長は候補に残す（任命でこの店舗が担当へ加わる）。
      predicates.add(
          cb.not(
              cb.and(
                  cb.isMember(managerRoleId, root.<Set<Long>>get("roleIds")),
                  cb.isMember(storeId, root.<Set<Long>>get("storeIds")))));
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

  /** 候補の母集団に属するか（「既にこの店舗の店長でない」は呼出側が先に判定する）。 */
  private static boolean isCandidate(PlatformUser user, Set<Long> hqRoleIds) {
    return user.getUserType() == UserType.STAFF
        && Boolean.TRUE.equals(user.getEnabled())
        && user.getStoreScopeType() == StoreScopeType.SPECIFIC_STORES
        && user.getRoleIds().stream().noneMatch(hqRoleIds::contains);
  }

  /** 店長ロール。既定ロールは名称が自然キーで、播種の正本は {@link SystemRole} 側にある。 */
  private Role requireStoreManagerRole() {
    return roleRepository
        .findByName(SystemRole.STORE_MANAGER.getRoleName())
        .orElseThrow(() -> new ServiceException("店長ロールが存在しません"));
  }

  /** 実在しない店舗宛の要求は空一覧でなく 404 に倒す（在否を応答の形で取り違えさせない）。 */
  private void requireStore(Long storeId) {
    if (!storeExistenceCheck.exists(storeId)) {
      throw new NotFoundException("店舗が見つかりません: " + storeId);
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  /**
   * 保存時の整合性違反を制約名で分類する。email 一意制約違反（同一メール二重送信レース）は重複エラー、店舗 FK 違反（事前検証と保存の間に
   * 店舗が消えるレース）は店舗エラーへ変換する（いずれも 400）。ロール FK は既定ロールが削除不能なので写像を持たない。
   */
  private PlatformUser save(PlatformUser user) {
    return IntegrityMappedSaves.save(
        repository,
        user,
        Map.of(
            DbConstraint.UQ_T_USERS_EMAIL,
            () -> new DuplicateStaffEmailException("このメールアドレスは既に登録されています"),
            DbConstraint.FK_T_USER_STORES_STORE,
            () -> new InvalidStoreScopeException("指定された店舗が存在しません")));
  }

  private static StoreManagerResponse toResponse(PlatformUser user) {
    return new StoreManagerResponse(
        user.getId(), user.getEmail(), user.getDisplayName(), user.getEnabled());
  }
}
