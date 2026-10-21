package com.kizuna.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlatformUserTest {

  private static User savedUser(UserType type) {
    User user =
        User.builder()
            .email(type == UserType.SERVICE ? null : "user@kizuna.test")
            .password(type == UserType.SERVICE ? null : "hash")
            .displayName("表示名")
            .enabled(true)
            .userType(type)
            .roleIds(type.holdsRoles() ? Set.of(1L) : Set.of())
            .build();
    user.setId(7L);
    return user;
  }

  private static PlatformUser.PlatformUserBuilder allStores() {
    return PlatformUser.builder()
        .user(savedUser(UserType.PLATFORM_USER))
        .storeScopeType(StoreScopeType.ALL_STORES)
        .storeIds(Set.of());
  }

  @Test
  @DisplayName("SPECIFIC_STORES で店舗集合が空だと不変条件違反で例外（種別による免除は無い）")
  void specificStoresWithEmptyStoreIdsThrows() {
    assertThatThrownBy(
            () ->
                allStores()
                    .storeScopeType(StoreScopeType.SPECIFIC_STORES)
                    .storeIds(Set.of())
                    .build())
        .isInstanceOf(InvalidStoreScopeException.class);
  }

  @Test
  @DisplayName("ALL_STORES で店舗集合が非空だと不変条件違反で例外")
  void allStoresWithNonEmptyStoreIdsThrows() {
    assertThatThrownBy(() -> allStores().storeIds(Set.of(1L)).build())
        .isInstanceOf(InvalidStoreScopeException.class);
  }

  @Test
  @DisplayName("作用域種別は必須")
  void nullScopeTypeThrows() {
    assertThatThrownBy(() -> allStores().storeScopeType(null).build())
        .isInstanceOf(InvalidStoreScopeException.class);
  }

  @Test
  @DisplayName("CAST / MEMBER には行を作れない（店舗作用域はロールで授権される種別だけが持つ）")
  void castAndMemberCannotHoldScope() {
    for (UserType type : Set.of(UserType.CAST, UserType.MEMBER)) {
      assertThatThrownBy(
              () ->
                  PlatformUser.builder()
                      .user(savedUser(type))
                      .storeScopeType(StoreScopeType.SPECIFIC_STORES)
                      .storeIds(Set.of(1L))
                      .build())
          .isInstanceOf(InvalidRoleGrantException.class);
    }
  }

  @Test
  @DisplayName("SERVICE は PLATFORM_USER と同じ語彙で ALL_STORES を持てる")
  void serviceHoldsAllStores() {
    PlatformUser scope =
        PlatformUser.builder()
            .user(savedUser(UserType.SERVICE))
            .storeScopeType(StoreScopeType.ALL_STORES)
            .storeIds(Set.of())
            .build();

    assertThat(scope.getUserId()).isEqualTo(7L);
    assertThat(scope.authorizes(99L)).isTrue();
  }

  @Test
  @DisplayName("保存前のユーザー（id 無し）には行を作れない")
  void unsavedUserThrows() {
    User user =
        User.builder()
            .email("user@kizuna.test")
            .password("hash")
            .displayName("表示名")
            .enabled(true)
            .userType(UserType.PLATFORM_USER)
            .roleIds(Set.of(1L))
            .build();
    assertThatThrownBy(
            () ->
                PlatformUser.builder()
                    .user(user)
                    .storeScopeType(StoreScopeType.ALL_STORES)
                    .storeIds(Set.of())
                    .build())
        .isInstanceOf(InvalidStoreScopeException.class);
  }

  @Test
  @DisplayName("ALL_STORES は任意の店舗 id を授権し、作用域表現は allStores")
  void allStoresAuthorizesAnyStore() {
    PlatformUser scope = allStores().build();

    assertThat(scope.authorizes(1L)).isTrue();
    assertThat(scope.authorizes(999L)).isTrue();
    assertThat(scope.toStoreScope().allStores()).isTrue();
    assertThat(scope.toStoreScope().storeIds()).isEmpty();
  }

  @Test
  @DisplayName("SPECIFIC_STORES はメンバー店舗のみを授権する")
  void specificStoresAuthorizesOnlyMembers() {
    PlatformUser scope =
        allStores().storeScopeType(StoreScopeType.SPECIFIC_STORES).storeIds(Set.of(1L, 2L)).build();

    assertThat(scope.authorizes(1L)).isTrue();
    assertThat(scope.authorizes(2L)).isTrue();
    assertThat(scope.authorizes(3L)).isFalse();
    assertThat(scope.toStoreScope().allStores()).isFalse();
    assertThat(scope.toStoreScope().storeIds()).containsExactlyInAnyOrder(1L, 2L);
  }

  @Test
  @DisplayName("reassignStores は店舗集合を更新し、構築時と同じ不変条件を検証する")
  void reassignStoresUpdatesScope() {
    PlatformUser scope = allStores().build();

    scope.reassignStores(StoreScopeType.SPECIFIC_STORES, Set.of(1L, 2L));

    assertThat(scope.getStoreScopeType()).isEqualTo(StoreScopeType.SPECIFIC_STORES);
    assertThat(scope.getStoreIds()).containsExactlyInAnyOrder(1L, 2L);
    assertThatThrownBy(() -> scope.reassignStores(StoreScopeType.SPECIFIC_STORES, Set.of()))
        .isInstanceOf(InvalidStoreScopeException.class);
    assertThatThrownBy(() -> scope.reassignStores(StoreScopeType.ALL_STORES, Set.of(1L)))
        .isInstanceOf(InvalidStoreScopeException.class);
  }
}
