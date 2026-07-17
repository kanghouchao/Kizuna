package com.kizuna.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlatformUserTest {

  /** STAFF・ALL_STORES・束 {1} の妥当な既定値。各テストが対象の属性だけ上書きする。 */
  private PlatformUser.PlatformUserBuilder staffBuilder() {
    return PlatformUser.builder()
        .email("user@kizuna.test")
        .password("hash")
        .displayName("表示名")
        .enabled(true)
        .userType(UserType.STAFF)
        .bundleIds(Set.of(1L))
        .storeScopeType(StoreScopeType.ALL_STORES)
        .storeIds(Set.of());
  }

  @Test
  @DisplayName("SPECIFIC_STORES で店舗集合が空だと不変条件違反で例外")
  void specificStoresWithEmptyStoreIdsThrows() {
    assertThatThrownBy(
            () ->
                staffBuilder()
                    .storeScopeType(StoreScopeType.SPECIFIC_STORES)
                    .storeIds(Set.of())
                    .build())
        .isInstanceOf(InvalidStoreScopeException.class);
  }

  @Test
  @DisplayName("ALL_STORES で店舗集合が非空だと不変条件違反で例外")
  void allStoresWithNonEmptyStoreIdsThrows() {
    assertThatThrownBy(
            () ->
                staffBuilder()
                    .storeScopeType(StoreScopeType.ALL_STORES)
                    .storeIds(Set.of(1L))
                    .build())
        .isInstanceOf(InvalidStoreScopeException.class);
  }

  @Test
  @DisplayName("STAFF は少なくとも 1 つの能力束が必要（空だとログイン後に到達可能端点が無くなるため拒否）")
  void staffWithoutBundlesThrows() {
    assertThatThrownBy(() -> staffBuilder().bundleIds(Set.of()).build())
        .isInstanceOf(InvalidBundleGrantException.class);
  }

  @Test
  @DisplayName("CAST は能力束を持てない（本人種別は能力モデルに入らない）")
  void castWithBundlesThrows() {
    assertThatThrownBy(
            () ->
                staffBuilder()
                    .userType(UserType.CAST)
                    .bundleIds(Set.of(1L))
                    .storeScopeType(StoreScopeType.SPECIFIC_STORES)
                    .storeIds(Set.of(1L))
                    .build())
        .isInstanceOf(InvalidBundleGrantException.class);
  }

  @Test
  @DisplayName("MEMBER は能力束を持てない")
  void memberWithBundlesThrows() {
    assertThatThrownBy(
            () ->
                staffBuilder()
                    .userType(UserType.MEMBER)
                    .bundleIds(Set.of(1L))
                    .storeScopeType(StoreScopeType.SPECIFIC_STORES)
                    .storeIds(Set.of(1L))
                    .build())
        .isInstanceOf(InvalidBundleGrantException.class);
  }

  @Test
  @DisplayName("CAST は束なしで構築できる")
  void castWithoutBundlesBuilds() {
    PlatformUser user =
        staffBuilder()
            .userType(UserType.CAST)
            .bundleIds(Set.of())
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(1L))
            .build();

    assertThat(user.getUserType()).isEqualTo(UserType.CAST);
    assertThat(user.getBundleIds()).isEmpty();
  }

  @Test
  @DisplayName("精算範囲なし（null）で精算店舗集合が非空だと不変条件違反で例外")
  void settlementStoresWithoutScopeTypeThrows() {
    assertThatThrownBy(() -> staffBuilder().settlementStoreIds(Set.of(1L)).build())
        .isInstanceOf(InvalidStoreScopeException.class);
  }

  @Test
  @DisplayName("精算範囲 SPECIFIC_STORES で精算店舗集合が空だと不変条件違反で例外")
  void settlementSpecificWithEmptyStoresThrows() {
    assertThatThrownBy(
            () -> staffBuilder().settlementScopeType(StoreScopeType.SPECIFIC_STORES).build())
        .isInstanceOf(InvalidStoreScopeException.class);
  }

  @Test
  @DisplayName("精算範囲 ALL_STORES で精算店舗集合が非空だと不変条件違反で例外")
  void settlementAllWithNonEmptyStoresThrows() {
    assertThatThrownBy(
            () ->
                staffBuilder()
                    .settlementScopeType(StoreScopeType.ALL_STORES)
                    .settlementStoreIds(Set.of(1L))
                    .build())
        .isInstanceOf(InvalidStoreScopeException.class);
  }

  @Test
  @DisplayName("精算範囲 SPECIFIC_STORES は精算店舗集合とともに構築できる（次元の表現 — #382 要件 5）")
  void settlementSpecificBuilds() {
    PlatformUser user =
        staffBuilder()
            .settlementScopeType(StoreScopeType.SPECIFIC_STORES)
            .settlementStoreIds(Set.of(2L))
            .build();

    assertThat(user.getSettlementScopeType()).isEqualTo(StoreScopeType.SPECIFIC_STORES);
    assertThat(user.getSettlementStoreIds()).containsExactly(2L);
  }

  @Test
  @DisplayName("ALL_STORES は任意の店舗 id を授権する")
  void allStoresAuthorizesAnyStore() {
    PlatformUser user = staffBuilder().build();

    assertThat(user.authorizes(1L)).isTrue();
    assertThat(user.authorizes(999L)).isTrue();
  }

  @Test
  @DisplayName("SPECIFIC_STORES はメンバー店舗のみを授権する")
  void specificStoresAuthorizesOnlyMembers() {
    PlatformUser user =
        staffBuilder()
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(1L, 2L))
            .build();

    assertThat(user.authorizes(1L)).isTrue();
    assertThat(user.authorizes(2L)).isTrue();
    assertThat(user.authorizes(3L)).isFalse();
  }

  @Test
  @DisplayName("email は構築時に小文字へ正規化される")
  void emailIsNormalizedToLowerCase() {
    PlatformUser user = staffBuilder().email("TANAKA.Hanako@KIZUNA.test").build();

    assertThat(user.getEmail()).isEqualTo("tanaka.hanako@kizuna.test");
  }

  @Test
  @DisplayName("reassignGrants は束・店舗集合・精算範囲を更新する")
  void reassignGrantsUpdatesAllGrantDimensions() {
    PlatformUser user = staffBuilder().build();

    user.reassignGrants(
        Set.of(2L, 3L),
        StoreScopeType.SPECIFIC_STORES,
        Set.of(1L),
        StoreScopeType.SPECIFIC_STORES,
        Set.of(1L, 2L));

    assertThat(user.getBundleIds()).containsExactlyInAnyOrder(2L, 3L);
    assertThat(user.getStoreScopeType()).isEqualTo(StoreScopeType.SPECIFIC_STORES);
    assertThat(user.getStoreIds()).containsExactly(1L);
    assertThat(user.getSettlementScopeType()).isEqualTo(StoreScopeType.SPECIFIC_STORES);
    assertThat(user.getSettlementStoreIds()).containsExactlyInAnyOrder(1L, 2L);
  }

  @Test
  @DisplayName("reassignGrants で精算範囲を null にすると精算店舗集合も空へ戻る")
  void reassignGrantsClearsSettlement() {
    PlatformUser user =
        staffBuilder()
            .settlementScopeType(StoreScopeType.SPECIFIC_STORES)
            .settlementStoreIds(Set.of(2L))
            .build();

    user.reassignGrants(Set.of(1L), StoreScopeType.ALL_STORES, Set.of(), null, Set.of());

    assertThat(user.getSettlementScopeType()).isNull();
    assertThat(user.getSettlementStoreIds()).isEmpty();
  }

  @Test
  @DisplayName("reassignGrants は STAFF の束空集合を不変条件違反で拒否する")
  void reassignGrantsWithEmptyBundlesThrows() {
    PlatformUser user = staffBuilder().build();

    assertThatThrownBy(
            () ->
                user.reassignGrants(Set.of(), StoreScopeType.ALL_STORES, Set.of(), null, Set.of()))
        .isInstanceOf(InvalidBundleGrantException.class);
  }

  @Test
  @DisplayName("reassignStores は店舗集合のみを更新し束を変えない（CAST 受諾用）")
  void reassignStoresUpdatesOnlyStoreScope() {
    PlatformUser user =
        staffBuilder()
            .userType(UserType.CAST)
            .bundleIds(Set.of())
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(1L))
            .build();

    user.reassignStores(StoreScopeType.SPECIFIC_STORES, Set.of(1L, 2L));

    assertThat(user.getStoreIds()).containsExactlyInAnyOrder(1L, 2L);
    assertThat(user.getBundleIds()).isEmpty();
    assertThat(user.getUserType()).isEqualTo(UserType.CAST);
  }

  @Test
  @DisplayName("reassignStores で SPECIFIC_STORES に空集合を渡すと不変条件違反で例外")
  void reassignStoresSpecificWithEmptyThrows() {
    PlatformUser user = staffBuilder().build();

    assertThatThrownBy(() -> user.reassignStores(StoreScopeType.SPECIFIC_STORES, Set.of()))
        .isInstanceOf(InvalidStoreScopeException.class);
  }

  @Test
  @DisplayName("stop は enabled を false に、resume は true に切り替える（行は保持され実行主体記録が残る）")
  void stopAndResumeToggleEnabled() {
    PlatformUser user = staffBuilder().build();

    user.stop();
    assertThat(user.getEnabled()).isFalse();

    user.resume();
    assertThat(user.getEnabled()).isTrue();
  }

  @Test
  @DisplayName("updateDisplayName は表示名を更新する")
  void updateDisplayNameUpdatesDisplayName() {
    PlatformUser user = staffBuilder().build();

    user.updateDisplayName("新しい表示名");

    assertThat(user.getDisplayName()).isEqualTo("新しい表示名");
  }

  @Test
  @DisplayName("changePassword はエンコード済みパスワードで置き換える")
  void changePasswordReplacesPassword() {
    PlatformUser user = staffBuilder().build();

    user.changePassword("new-encoded-hash");

    assertThat(user.getPassword()).isEqualTo("new-encoded-hash");
  }
}
