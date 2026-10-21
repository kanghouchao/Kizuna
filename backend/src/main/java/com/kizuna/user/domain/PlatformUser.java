package com.kizuna.user.domain;

import com.kizuna.shared.persistence.BaseEntity;
import com.kizuna.shared.storescope.StoreScope;
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
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

/**
 * 平台ユーザーの店舗作用域（担当店舗集合）。{@link User} と 1:1 で、ロールで授権される種別（PLATFORM_USER / SERVICE）だけが行を持つ。
 *
 * <p>店舗集合はトークンに載せず、要求ごとに DB から解決する — 再割当が再ログインを待たずに次の要求から効くための構造で、ロール（authority
 * はトークンに鋳造される）とは反映の時点が異なる。
 *
 * <p>不変条件（構築時と再割当時に検証、違反は 400 系ドメイン例外）: SPECIFIC_STORES は店舗 1 件以上、ALL_STORES は店舗なし（{@link
 * InvalidStoreScopeException}）。CAST / MEMBER に行を作ることは構築時に拒む（{@link InvalidRoleGrantException}）。
 */
@Entity
@Table(name = "t_platform_users")
@Getter
@NoArgsConstructor
public class PlatformUser extends BaseEntity {

  @Column(name = "user_id", nullable = false, updatable = false, unique = true)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "store_scope_type", nullable = false, length = 20)
  private StoreScopeType storeScopeType;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "t_platform_user_stores",
      joinColumns = @JoinColumn(name = "platform_user_id"))
  @Column(name = "store_id")
  @BatchSize(size = 25)
  private Set<Long> storeIds = new HashSet<>();

  @Builder
  public PlatformUser(User user, StoreScopeType storeScopeType, Set<Long> storeIds) {
    if (user == null || user.getId() == null) {
      throw new InvalidStoreScopeException("店舗作用域の主体となるユーザーが保存されていません");
    }
    if (!user.getUserType().holdsRoles()) {
      throw new InvalidRoleGrantException("CAST / MEMBER に店舗作用域を授権できません");
    }
    Set<Long> stores = storeIds == null ? Set.of() : storeIds;
    validateScope(storeScopeType, stores);
    this.userId = user.getId();
    this.storeScopeType = storeScopeType;
    this.storeIds = new HashSet<>(stores);
  }

  /** 担当店舗集合を再割当てする。構築時と同一の不変条件を検証する。 */
  public void reassignStores(StoreScopeType storeScopeType, Set<Long> storeIds) {
    Set<Long> stores = storeIds == null ? Set.of() : storeIds;
    validateScope(storeScopeType, stores);
    this.storeScopeType = storeScopeType;
    this.storeIds = new HashSet<>(stores);
  }

  /** 指定店舗を授権するか。ALL_STORES は常に true、SPECIFIC_STORES は店舗集合のメンバーのみ true。 */
  public boolean authorizes(Long storeId) {
    return storeScopeType == StoreScopeType.ALL_STORES || storeIds.contains(storeId);
  }

  /** 共有カーネルの集合作用域表現へ写す（要求ごとの解決器が保持器へ差し込む値）。 */
  public StoreScope toStoreScope() {
    return new StoreScope(storeScopeType == StoreScopeType.ALL_STORES, Set.copyOf(storeIds));
  }

  private static void validateScope(StoreScopeType storeScopeType, Set<Long> stores) {
    if (storeScopeType == null) {
      throw new InvalidStoreScopeException("店舗作用域の種別は必須です");
    }
    if (storeScopeType == StoreScopeType.SPECIFIC_STORES && stores.isEmpty()) {
      throw new InvalidStoreScopeException("SPECIFIC_STORES の授権には少なくとも 1 つの店舗が必要です");
    }
    if (storeScopeType == StoreScopeType.ALL_STORES && !stores.isEmpty()) {
      throw new InvalidStoreScopeException("ALL_STORES の授権に個別店舗を指定できません");
    }
  }
}
