package com.kizuna.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlatformUserTest {

  /** STAFF・ALL_STORES・ロール {1} の妥当な既定値。各テストが対象の属性だけ上書きする。 */
  private PlatformUser.PlatformUserBuilder staffBuilder() {
    return PlatformUser.builder()
        .email("user@kizuna.test")
        .password("hash")
        .displayName("表示名")
        .enabled(true)
        .userType(UserType.STAFF)
        .roleIds(Set.of(1L))
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
  @DisplayName("STAFF は少なくとも 1 つのロールが必要（空だとログイン後に到達可能端点が無くなるため拒否）")
  void staffWithoutRolesThrows() {
    assertThatThrownBy(() -> staffBuilder().roleIds(Set.of()).build())
        .isInstanceOf(InvalidRoleGrantException.class);
  }

  @Test
  @DisplayName("CAST はロールを持てない（本人種別は権限モデルに入らない）")
  void castWithRolesThrows() {
    assertThatThrownBy(
            () ->
                staffBuilder()
                    .userType(UserType.CAST)
                    .roleIds(Set.of(1L))
                    .storeScopeType(StoreScopeType.SPECIFIC_STORES)
                    .storeIds(Set.of(1L))
                    .build())
        .isInstanceOf(InvalidRoleGrantException.class);
  }

  @Test
  @DisplayName("MEMBER はロールを持てない")
  void memberWithRolesThrows() {
    assertThatThrownBy(
            () ->
                staffBuilder()
                    .userType(UserType.MEMBER)
                    .roleIds(Set.of(1L))
                    .storeScopeType(StoreScopeType.SPECIFIC_STORES)
                    .storeIds(Set.of(1L))
                    .build())
        .isInstanceOf(InvalidRoleGrantException.class);
  }

  @Test
  @DisplayName("MEMBER は SPECIFIC_STORES + 空集合で構築でき、どの店舗も授権しない（登録時点で紐づけ店舗なし）")
  void memberWithEmptySpecificStoresBuildsAndAuthorizesNothing() {
    PlatformUser user =
        staffBuilder()
            .userType(UserType.MEMBER)
            .roleIds(Set.of())
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of())
            .build();

    assertThat(user.getUserType()).isEqualTo(UserType.MEMBER);
    assertThat(user.authorizes(1L)).isFalse();
  }

  @Test
  @DisplayName("MEMBER に ALL_STORES を授権できない（authorizes が無条件 true になる fail-open を塞ぐ）")
  void memberWithAllStoresThrows() {
    assertThatThrownBy(
            () ->
                staffBuilder()
                    .userType(UserType.MEMBER)
                    .roleIds(Set.of())
                    .storeScopeType(StoreScopeType.ALL_STORES)
                    .storeIds(Set.of())
                    .build())
        .isInstanceOf(InvalidStoreScopeException.class);
  }

  @Test
  @DisplayName("CAST はロールなしで構築できる")
  void castWithoutRolesBuilds() {
    PlatformUser user =
        staffBuilder()
            .userType(UserType.CAST)
            .roleIds(Set.of())
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(1L))
            .build();

    assertThat(user.getUserType()).isEqualTo(UserType.CAST);
    assertThat(user.getRoleIds()).isEmpty();
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
  @DisplayName("reassignGrants はロール・店舗集合を更新する")
  void reassignGrantsUpdatesAllGrantDimensions() {
    PlatformUser user = staffBuilder().build();

    user.reassignGrants(Set.of(2L, 3L), StoreScopeType.SPECIFIC_STORES, Set.of(1L));

    assertThat(user.getRoleIds()).containsExactlyInAnyOrder(2L, 3L);
    assertThat(user.getStoreScopeType()).isEqualTo(StoreScopeType.SPECIFIC_STORES);
    assertThat(user.getStoreIds()).containsExactly(1L);
  }

  @Test
  @DisplayName("reassignGrants は STAFF のロール空集合を不変条件違反で拒否する")
  void reassignGrantsWithEmptyRolesThrows() {
    PlatformUser user = staffBuilder().build();

    assertThatThrownBy(() -> user.reassignGrants(Set.of(), StoreScopeType.ALL_STORES, Set.of()))
        .isInstanceOf(InvalidRoleGrantException.class);
  }

  @Test
  @DisplayName("reassignStores は店舗集合のみを更新しロールを変えない（CAST 受諾用）")
  void reassignStoresUpdatesOnlyStoreScope() {
    PlatformUser user =
        staffBuilder()
            .userType(UserType.CAST)
            .roleIds(Set.of())
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(1L))
            .build();

    user.reassignStores(StoreScopeType.SPECIFIC_STORES, Set.of(1L, 2L));

    assertThat(user.getStoreIds()).containsExactlyInAnyOrder(1L, 2L);
    assertThat(user.getRoleIds()).isEmpty();
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

  @Test
  @DisplayName("未連携の身分は linkLine で LINE ユーザー ID を持つ")
  void linkLineSetsLineUserId() {
    PlatformUser user = staffBuilder().build();
    assertThat(user.getLineUserId()).isNull();

    user.linkLine("U-line-1");

    assertThat(user.getLineUserId()).isEqualTo("U-line-1");
  }

  @Test
  @DisplayName("連携済みの身分への再連携は 409 相当のドメイン例外（付け替えを許すと連携先を無断で移せる）")
  void linkLineOnAlreadyLinkedUserThrows() {
    PlatformUser user = staffBuilder().build();
    user.linkLine("U-line-1");

    assertThatThrownBy(() -> user.linkLine("U-line-2"))
        .isInstanceOf(LineAlreadyLinkedException.class);
    assertThat(user.getLineUserId()).isEqualTo("U-line-1");
  }
}
