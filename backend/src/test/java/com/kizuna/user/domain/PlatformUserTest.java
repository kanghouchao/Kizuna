package com.kizuna.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlatformUserTest {

  private PlatformUser.PlatformUserBuilder seedBuilder() {
    return PlatformUser.builder()
        .email("user@kizuna.test")
        .password("hash")
        .displayName("表示名")
        .enabled(true)
        .role(PlatformRole.HQ_ADMIN);
  }

  @Test
  @DisplayName("SPECIFIC_STORES で店舗集合が空だと不変条件違反で例外")
  void specificStoresWithEmptyStoreIdsThrows() {
    assertThatThrownBy(
            () ->
                seedBuilder()
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
                seedBuilder()
                    .storeScopeType(StoreScopeType.ALL_STORES)
                    .storeIds(Set.of(1L))
                    .build())
        .isInstanceOf(InvalidStoreScopeException.class);
  }

  @Test
  @DisplayName("ALL_STORES は任意の店舗 id を授権する")
  void allStoresAuthorizesAnyStore() {
    PlatformUser user =
        seedBuilder().storeScopeType(StoreScopeType.ALL_STORES).storeIds(Set.of()).build();

    assertThat(user.authorizes(1L)).isTrue();
    assertThat(user.authorizes(999L)).isTrue();
  }

  @Test
  @DisplayName("email は構築時に小文字へ正規化される")
  void emailIsNormalizedToLowerCase() {
    PlatformUser user =
        seedBuilder()
            .email("TANAKA.Hanako@KIZUNA.test")
            .storeScopeType(StoreScopeType.ALL_STORES)
            .storeIds(Set.of())
            .build();

    assertThat(user.getEmail()).isEqualTo("tanaka.hanako@kizuna.test");
  }

  @Test
  @DisplayName("SPECIFIC_STORES はメンバー店舗のみを授権する")
  void specificStoresAuthorizesOnlyMembers() {
    PlatformUser user =
        seedBuilder()
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(1L, 2L))
            .build();

    assertThat(user.authorizes(1L)).isTrue();
    assertThat(user.authorizes(2L)).isTrue();
    assertThat(user.authorizes(3L)).isFalse();
  }

  @Test
  @DisplayName("reassign はロール・店舗集合種別・店舗集合を更新する")
  void reassignUpdatesRoleAndScope() {
    PlatformUser user =
        seedBuilder().storeScopeType(StoreScopeType.ALL_STORES).storeIds(Set.of()).build();

    user.reassign(PlatformRole.STORE_MANAGER, StoreScopeType.SPECIFIC_STORES, Set.of(1L, 2L));

    assertThat(user.getRole()).isEqualTo(PlatformRole.STORE_MANAGER);
    assertThat(user.getStoreScopeType()).isEqualTo(StoreScopeType.SPECIFIC_STORES);
    assertThat(user.getStoreIds()).containsExactlyInAnyOrder(1L, 2L);
    assertThat(user.authorizes(1L)).isTrue();
    assertThat(user.authorizes(3L)).isFalse();
  }

  @Test
  @DisplayName("reassign で SPECIFIC_STORES に空集合を渡すと不変条件違反で例外")
  void reassignSpecificStoresWithEmptyStoreIdsThrows() {
    PlatformUser user =
        seedBuilder().storeScopeType(StoreScopeType.ALL_STORES).storeIds(Set.of()).build();

    assertThatThrownBy(
            () -> user.reassign(PlatformRole.STORE_STAFF, StoreScopeType.SPECIFIC_STORES, Set.of()))
        .isInstanceOf(InvalidStoreScopeException.class);
  }

  @Test
  @DisplayName("reassign で ALL_STORES に非空集合を渡すと不変条件違反で例外")
  void reassignAllStoresWithNonEmptyStoreIdsThrows() {
    PlatformUser user =
        seedBuilder().storeScopeType(StoreScopeType.SPECIFIC_STORES).storeIds(Set.of(1L)).build();

    assertThatThrownBy(
            () -> user.reassign(PlatformRole.HQ_ADMIN, StoreScopeType.ALL_STORES, Set.of(1L)))
        .isInstanceOf(InvalidStoreScopeException.class);
  }
}
