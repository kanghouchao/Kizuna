package com.kizuna.user.domain;

import com.kizuna.shared.persistence.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * プラットフォーム共通ユーザー集約。email でログインし、授権は「能力束 × 担当店舗集合 ×（必要時）精算範囲」で表す。
 *
 * <p>本人種別（{@link UserType}）が STAFF のユーザーだけが能力束を持ち、CAST / MEMBER は能力モデルに入らない（既定）。束は跨集約 ID 参照 （{@link
 * CapabilityBundle}）。停止は {@link #stop()}（enabled=false）であり、行を削除しないことで過去の実行主体の記録を保持する。
 *
 * <p>不変条件（構築時と再割当時に検証、違反は 400 系ドメイン例外）:
 *
 * <ul>
 *   <li>店舗集合: {@code SPECIFIC_STORES} は非空、{@code ALL_STORES} は空（{@link InvalidStoreScopeException}）
 *   <li>能力束: STAFF は 1 束以上、CAST/MEMBER は空（{@link InvalidBundleGrantException}）
 *   <li>精算範囲: null（範囲なし）は精算店舗集合が空、{@code SPECIFIC_STORES} は非空、{@code ALL_STORES} は空（{@link
 *       InvalidStoreScopeException}）
 * </ul>
 */
@Entity
@Table(name = "t_users")
@Getter
@NoArgsConstructor
public class PlatformUser extends BaseEntity {

  @Column(nullable = false, unique = true, length = 255)
  private String email;

  @Column(nullable = false)
  private String password;

  @Column(name = "display_name", nullable = false, length = 150)
  private String displayName;

  @Column(nullable = false)
  private Boolean enabled = true;

  @Enumerated(EnumType.STRING)
  @Column(name = "user_type", nullable = false, length = 20)
  private UserType userType;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "t_user_bundles", joinColumns = @JoinColumn(name = "platform_user_id"))
  @Column(name = "bundle_id")
  private Set<Long> bundleIds = new HashSet<>();

  @Enumerated(EnumType.STRING)
  @Column(name = "store_scope_type", nullable = false, length = 20)
  private StoreScopeType storeScopeType;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "t_user_stores", joinColumns = @JoinColumn(name = "platform_user_id"))
  @Column(name = "store_id")
  private Set<Long> storeIds = new HashSet<>();

  /** 精算範囲種別。null は「精算範囲なし」（経理系能力を持たない通常ユーザーの既定）。 */
  @Enumerated(EnumType.STRING)
  @Column(name = "settlement_scope_type", length = 20)
  private StoreScopeType settlementScopeType;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "t_user_settlement_stores",
      joinColumns = @JoinColumn(name = "platform_user_id"))
  @Column(name = "store_id")
  private Set<Long> settlementStoreIds = new HashSet<>();

  @Builder
  public PlatformUser(
      String email,
      String password,
      String displayName,
      boolean enabled,
      UserType userType,
      Set<Long> bundleIds,
      StoreScopeType storeScopeType,
      Set<Long> storeIds,
      StoreScopeType settlementScopeType,
      Set<Long> settlementStoreIds) {
    Set<Long> bundles = bundleIds == null ? Set.of() : bundleIds;
    Set<Long> stores = storeIds == null ? Set.of() : storeIds;
    Set<Long> settlementStores = settlementStoreIds == null ? Set.of() : settlementStoreIds;
    validateBundleGrant(userType, bundles);
    validateScope(storeScopeType, stores);
    validateSettlementScope(settlementScopeType, settlementStores);
    this.email = email == null ? null : email.toLowerCase(Locale.ROOT);
    this.password = password;
    this.displayName = displayName;
    this.enabled = enabled;
    this.userType = userType;
    this.bundleIds = new HashSet<>(bundles);
    this.storeScopeType = storeScopeType;
    this.storeIds = new HashSet<>(stores);
    this.settlementScopeType = settlementScopeType;
    this.settlementStoreIds = new HashSet<>(settlementStores);
  }

  /** 授権（能力束・担当店舗集合・精算範囲）を再割当てする。構築時と同一の不変条件を検証する（本人属性は変更しない）。 */
  public void reassignGrants(
      Set<Long> bundleIds,
      StoreScopeType storeScopeType,
      Set<Long> storeIds,
      StoreScopeType settlementScopeType,
      Set<Long> settlementStoreIds) {
    Set<Long> bundles = bundleIds == null ? Set.of() : bundleIds;
    Set<Long> stores = storeIds == null ? Set.of() : storeIds;
    Set<Long> settlementStores = settlementStoreIds == null ? Set.of() : settlementStoreIds;
    validateBundleGrant(this.userType, bundles);
    validateScope(storeScopeType, stores);
    validateSettlementScope(settlementScopeType, settlementStores);
    this.bundleIds = new HashSet<>(bundles);
    this.storeScopeType = storeScopeType;
    this.storeIds = new HashSet<>(stores);
    this.settlementScopeType = settlementScopeType;
    this.settlementStoreIds = new HashSet<>(settlementStores);
  }

  /** 担当店舗集合のみを再割当てする（CAST の招待受諾が所属店舗を冪等 union する用途。束・精算範囲は変更しない）。 */
  public void reassignStores(StoreScopeType storeScopeType, Set<Long> storeIds) {
    Set<Long> stores = storeIds == null ? Set.of() : storeIds;
    validateScope(storeScopeType, stores);
    this.storeScopeType = storeScopeType;
    this.storeIds = new HashSet<>(stores);
  }

  /** 停止する（enabled=false）。行は削除せず、過去の実行主体の記録を保持する。 */
  public void stop() {
    this.enabled = false;
  }

  /** 再開する（enabled=true）。 */
  public void resume() {
    this.enabled = true;
  }

  /** 表示名を更新する（自己プロフィール更新用。不変条件なし）。 */
  public void updateDisplayName(String displayName) {
    this.displayName = displayName;
  }

  /** エンコード済みパスワードで置き換える（呼び出し側で符号化済みであること）。 */
  public void changePassword(String encodedPassword) {
    this.password = encodedPassword;
  }

  private static void validateBundleGrant(UserType userType, Set<Long> bundles) {
    if (userType == UserType.STAFF && bundles.isEmpty()) {
      throw new InvalidBundleGrantException("STAFF には少なくとも 1 つの能力束が必要です");
    }
    if (userType != UserType.STAFF && !bundles.isEmpty()) {
      throw new InvalidBundleGrantException("CAST / MEMBER に能力束を授与できません");
    }
  }

  private static void validateScope(StoreScopeType storeScopeType, Set<Long> stores) {
    if (storeScopeType == StoreScopeType.SPECIFIC_STORES && stores.isEmpty()) {
      throw new InvalidStoreScopeException("SPECIFIC_STORES の授権には少なくとも 1 つの店舗が必要です");
    }
    if (storeScopeType == StoreScopeType.ALL_STORES && !stores.isEmpty()) {
      throw new InvalidStoreScopeException("ALL_STORES の授権に個別店舗を指定できません");
    }
  }

  private static void validateSettlementScope(
      StoreScopeType settlementScopeType, Set<Long> settlementStores) {
    if (settlementScopeType == null && !settlementStores.isEmpty()) {
      throw new InvalidStoreScopeException("精算範囲なしの授権に精算店舗を指定できません");
    }
    if (settlementScopeType != null) {
      validateScope(settlementScopeType, settlementStores);
    }
  }

  /** 指定店舗を授権するか。ALL_STORES は常に true、SPECIFIC_STORES は店舗集合のメンバーのみ true。 */
  public boolean authorizes(Long storeId) {
    return storeScopeType == StoreScopeType.ALL_STORES || storeIds.contains(storeId);
  }
}
