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
}
