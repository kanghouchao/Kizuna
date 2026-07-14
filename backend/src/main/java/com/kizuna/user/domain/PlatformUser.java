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
 * プラットフォーム共通ユーザー集約。email でログインし、授権は「ロール×店舗集合」で表す（1 ユーザー = 1 ロール + 1 店舗集合）。
 *
 * <p>店舗集合の不変条件は構築時に検証する: {@code SPECIFIC_STORES} は店舗集合が非空、{@code ALL_STORES} は店舗集合が空。 違反は {@link
 * InvalidStoreScopeException}。
 */
@Entity
@Table(name = "platform_users")
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
  @Column(nullable = false, length = 30)
  private PlatformRole role;

  @Enumerated(EnumType.STRING)
  @Column(name = "store_scope_type", nullable = false, length = 20)
  private StoreScopeType storeScopeType;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "platform_user_stores",
      joinColumns = @JoinColumn(name = "platform_user_id"))
  @Column(name = "store_id")
  private Set<Long> storeIds = new HashSet<>();

  @Builder
  public PlatformUser(
      String email,
      String password,
      String displayName,
      boolean enabled,
      PlatformRole role,
      StoreScopeType storeScopeType,
      Set<Long> storeIds) {
    Set<Long> stores = storeIds == null ? Set.of() : storeIds;
    validateScope(storeScopeType, stores);
    this.email = email == null ? null : email.toLowerCase(Locale.ROOT);
    this.password = password;
    this.displayName = displayName;
    this.enabled = enabled;
    this.role = role;
    this.storeScopeType = storeScopeType;
    this.storeIds = new HashSet<>(stores);
  }

  /** ロール×店舗集合を再割当てする。構築時と同一の不変条件を検証する（email/password/displayName は変更しない）。 */
  public void reassign(PlatformRole role, StoreScopeType storeScopeType, Set<Long> storeIds) {
    Set<Long> stores = storeIds == null ? Set.of() : storeIds;
    validateScope(storeScopeType, stores);
    this.role = role;
    this.storeScopeType = storeScopeType;
    this.storeIds = new HashSet<>(stores);
  }

  private static void validateScope(StoreScopeType storeScopeType, Set<Long> stores) {
    if (storeScopeType == StoreScopeType.SPECIFIC_STORES && stores.isEmpty()) {
      throw new InvalidStoreScopeException("SPECIFIC_STORES の授権には少なくとも 1 つの店舗が必要です");
    }
    if (storeScopeType == StoreScopeType.ALL_STORES && !stores.isEmpty()) {
      throw new InvalidStoreScopeException("ALL_STORES の授権に個別店舗を指定できません");
    }
  }

  /** 指定店舗を授権するか。ALL_STORES は常に true、SPECIFIC_STORES は店舗集合のメンバーのみ true。 */
  public boolean authorizes(Long storeId) {
    return storeScopeType == StoreScopeType.ALL_STORES || storeIds.contains(storeId);
  }
}
