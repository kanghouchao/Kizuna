package com.kizuna.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.ServiceException;
import java.time.OffsetDateTime;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserTest {

  /** STAFF・空の店舗集合・ロール {1} の妥当な既定値。各テストが対象の属性だけ上書きする。 */
  private User.UserBuilder staffBuilder() {
    return User.builder()
        .email("user@kizuna.test")
        .password("hash")
        .displayName("表示名")
        .enabled(true)
        .userType(UserType.STAFF)
        .roleIds(Set.of(1L))
        .storeIds(Set.of());
  }

  @Test
  @DisplayName("返した授権の集合は変更できず、変更は reassignGrants / reassignStores を通るしかない")
  void grantSetsCannotBeModifiedThroughTheGetters() {
    User user = staffBuilder().build();

    assertThatThrownBy(() -> user.getRoleIds().add(99L))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> user.getStoreIds().add(99L))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(user.getRoleIds()).containsExactly(1L);
    assertThat(user.getStoreIds()).isEmpty();
  }

  @Test
  @DisplayName("店舗集合はどの本人種別でも空で構築でき、空はどの店舗も授権しない（全店舗を意味しない）")
  void emptyStoreSetBuildsForEveryTypeAndAuthorizesNothing() {
    for (UserType type : UserType.values()) {
      var builder = type.holdsRoles() ? staffBuilder() : staffBuilder().roleIds(Set.of());
      if (type == UserType.SERVICE) builder = serviceBuilder();
      User user = builder.userType(type).storeIds(Set.of()).build();

      assertThat(user.getStoreIds()).as(type.name()).isEmpty();
      assertThat(user.authorizes(1L)).as(type.name()).isFalse();
      assertThat(user.authorizes(null)).as(type.name()).isFalse();
    }
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
                    .storeIds(Set.of(1L))
                    .build())
        .isInstanceOf(InvalidRoleGrantException.class);
  }

  @Test
  @DisplayName("MEMBER は空の店舗集合で構築でき、どの店舗も授権しない（登録時点で紐づけ店舗なし）")
  void memberWithEmptyStoreSetBuildsAndAuthorizesNothing() {
    User user =
        staffBuilder().userType(UserType.MEMBER).roleIds(Set.of()).storeIds(Set.of()).build();

    assertThat(user.getUserType()).isEqualTo(UserType.MEMBER);
    assertThat(user.authorizes(1L)).isFalse();
  }

  @Test
  @DisplayName("CAST はロールなしで構築できる")
  void castWithoutRolesBuilds() {
    User user =
        staffBuilder().userType(UserType.CAST).roleIds(Set.of()).storeIds(Set.of(1L)).build();

    assertThat(user.getUserType()).isEqualTo(UserType.CAST);
    assertThat(user.getRoleIds()).isEmpty();
  }

  @Test
  @DisplayName("店舗集合のメンバー店舗のみを授権する")
  void authorizesOnlyMembers() {
    User user = staffBuilder().storeIds(Set.of(1L, 2L)).build();

    assertThat(user.authorizes(1L)).isTrue();
    assertThat(user.authorizes(2L)).isTrue();
    assertThat(user.authorizes(3L)).isFalse();
  }

  @Test
  @DisplayName("email は構築時に小文字へ正規化される")
  void emailIsNormalizedToLowerCase() {
    User user = staffBuilder().email("TANAKA.Hanako@KIZUNA.test").build();

    assertThat(user.getEmail()).isEqualTo("tanaka.hanako@kizuna.test");
  }

  @Test
  @DisplayName("reassignGrants はロール・店舗集合を更新する")
  void reassignGrantsUpdatesAllGrantDimensions() {
    User user = staffBuilder().build();

    user.reassignGrants(Set.of(2L, 3L), Set.of(1L));

    assertThat(user.getRoleIds()).containsExactlyInAnyOrder(2L, 3L);
    assertThat(user.getStoreIds()).containsExactly(1L);
  }

  @Test
  @DisplayName("reassignGrants はロール集合が変わると資格情報の版を 1 つ進める（実効権限の変更 — ADR 0022）")
  void reassignGrantsWithChangedRolesBumpsCredentialVersion() {
    User user = staffBuilder().build();

    user.reassignGrants(Set.of(1L, 2L), Set.of());

    assertThat(user.getCredentialVersion()).isEqualTo(1L);
  }

  @Test
  @DisplayName("reassignGrants は店舗範囲だけの変更や同じロール集合の再提出では版を進めない")
  void reassignGrantsWithSameRolesKeepsCredentialVersion() {
    User user = staffBuilder().build();

    user.reassignGrants(Set.of(1L), Set.of(5L));
    user.reassignGrants(Set.of(1L), Set.of(5L));

    assertThat(user.getCredentialVersion()).isZero();
  }

  @Test
  @DisplayName("reassignGrants は STAFF のロール空集合を不変条件違反で拒否する")
  void reassignGrantsWithEmptyRolesThrows() {
    User user = staffBuilder().build();

    assertThatThrownBy(() -> user.reassignGrants(Set.of(), Set.of()))
        .isInstanceOf(InvalidRoleGrantException.class);
  }

  @Test
  @DisplayName("reassignStores は店舗集合のみを更新しロールを変えない（CAST 受諾用）")
  void reassignStoresUpdatesOnlyStoreScope() {
    User user =
        staffBuilder().userType(UserType.CAST).roleIds(Set.of()).storeIds(Set.of(1L)).build();

    user.reassignStores(Set.of(1L, 2L));

    assertThat(user.getStoreIds()).containsExactlyInAnyOrder(1L, 2L);
    assertThat(user.getRoleIds()).isEmpty();
    assertThat(user.getUserType()).isEqualTo(UserType.CAST);
  }

  @Test
  @DisplayName("reassignStores / reassignGrants は空の店舗集合を受け付ける")
  void reassigningToAnEmptyStoreSetIsAllowed() {
    User user = staffBuilder().storeIds(Set.of(1L)).build();

    user.reassignStores(Set.of());
    assertThat(user.getStoreIds()).isEmpty();

    user.reassignGrants(Set.of(1L), Set.of(2L));
    user.reassignGrants(Set.of(1L), Set.of());
    assertThat(user.getStoreIds()).isEmpty();
    assertThat(user.authorizes(2L)).isFalse();
  }

  @Test
  @DisplayName("stop は enabled を false に、resume は true に切り替える（行は保持され実行主体記録が残る）")
  void stopAndResumeToggleEnabled() {
    User user = staffBuilder().build();

    user.stop();
    assertThat(user.getEnabled()).isFalse();

    user.resume();
    assertThat(user.getEnabled()).isTrue();
  }

  @Test
  @DisplayName("stop は資格情報の版を増やし、resume は増やさない（再開で停止前セッションは復活しない — ADR 0022）")
  void stopBumpsCredentialVersionAndResumeDoesNot() {
    User user = staffBuilder().build();
    assertThat(user.getCredentialVersion()).isEqualTo(0L);

    user.stop();
    assertThat(user.getCredentialVersion()).isEqualTo(1L);

    user.stop();
    assertThat(user.getCredentialVersion()).isEqualTo(1L);

    user.resume();
    assertThat(user.getCredentialVersion()).isEqualTo(1L);
  }

  @Test
  @DisplayName("changePassword は資格情報の版を増やす（全端末の既存セッションを失効させる — ADR 0022）")
  void changePasswordBumpsCredentialVersion() {
    User user = staffBuilder().build();

    user.changePassword("new-encoded-hash");

    assertThat(user.getCredentialVersion()).isEqualTo(1L);
  }

  @Test
  @DisplayName("invalidateSessions は資格情報の版をちょうど 1 つ進める（緊急昇格の撤回時の失効 — ADR 0022）")
  void invalidateSessionsBumpsCredentialVersionByOne() {
    User user = staffBuilder().build();
    assertThat(user.getCredentialVersion()).isZero();

    user.invalidateSessions();

    assertThat(user.getCredentialVersion()).isEqualTo(1L);
  }

  @Test
  @DisplayName("updateDisplayName は表示名を更新する")
  void updateDisplayNameUpdatesDisplayName() {
    User user = staffBuilder().build();

    user.updateDisplayName("新しい表示名");

    assertThat(user.getDisplayName()).isEqualTo("新しい表示名");
  }

  @Test
  @DisplayName("changePassword はエンコード済みパスワードで置き換える")
  void changePasswordReplacesPassword() {
    User user = staffBuilder().build();

    user.changePassword("new-encoded-hash");

    assertThat(user.getPassword()).isEqualTo("new-encoded-hash");
  }

  @Test
  @DisplayName("未連携の身分は linkLine で LINE ユーザー ID を持つ")
  void linkLineSetsLineUserId() {
    User user = staffBuilder().build();
    assertThat(user.getLineUserId()).isNull();

    user.linkLine("U-line-1");

    assertThat(user.getLineUserId()).isEqualTo("U-line-1");
  }

  @Test
  @DisplayName("連携済みの身分への再連携は 409 相当のドメイン例外（付け替えを許すと連携先を無断で移せる）")
  void linkLineOnAlreadyLinkedUserThrows() {
    User user = staffBuilder().build();
    user.linkLine("U-line-1");

    assertThatThrownBy(() -> user.linkLine("U-line-2"))
        .isInstanceOf(LineAlreadyLinkedException.class);
    assertThat(user.getLineUserId()).isEqualTo("U-line-1");
  }

  /** SERVICE・空の店舗集合・ロール {1} の妥当な既定値。資格情報を持たないことが構築の前提。 */
  private User.UserBuilder serviceBuilder() {
    return User.builder()
        .displayName("夜間バッチ")
        .enabled(true)
        .userType(UserType.SERVICE)
        .roleIds(Set.of(1L))
        .storeIds(Set.of());
  }

  @Test
  @DisplayName("SERVICE は email・パスワードなしで構築できる（資格情報を持たないことが構造上の事実）")
  void serviceWithoutCredentialsBuilds() {
    User user = serviceBuilder().build();

    assertThat(user.getUserType()).isEqualTo(UserType.SERVICE);
    assertThat(user.getEmail()).isNull();
    assertThat(user.getPassword()).isNull();
  }

  @Test
  @DisplayName("SERVICE は email を持てない（照合できる値が行に無いことが対話ログイン不可の第一の防線）")
  void serviceWithEmailThrows() {
    assertThatThrownBy(() -> serviceBuilder().email("svc@kizuna.test").build())
        .isInstanceOf(InvalidCredentialAssignmentException.class);
  }

  @Test
  @DisplayName("SERVICE はパスワードを持てない")
  void serviceWithPasswordThrows() {
    assertThatThrownBy(() -> serviceBuilder().password("hash").build())
        .isInstanceOf(InvalidCredentialAssignmentException.class);
  }

  @Test
  @DisplayName("SERVICE の changePassword は不変条件違反で拒否される（構築後にも資格情報を持てない）")
  void serviceChangePasswordThrows() {
    User user = serviceBuilder().build();

    assertThatThrownBy(() -> user.changePassword("new-encoded-hash"))
        .isInstanceOf(InvalidCredentialAssignmentException.class);
    assertThat(user.getPassword()).isNull();
    assertThat(user.getCredentialVersion()).isZero();
  }

  @Test
  @DisplayName("SERVICE は LINE を連携済みとして構築できない（対話ログインの同一性根拠を一切持たない）")
  void serviceWithLineUserIdThrows() {
    assertThatThrownBy(() -> serviceBuilder().lineUserId("U-line-1").build())
        .isInstanceOf(InvalidCredentialAssignmentException.class);
  }

  @Test
  @DisplayName("SERVICE への linkLine は不変条件違反で拒否される（構築後にも LINE の同一性根拠を持てない）")
  void serviceLinkLineThrows() {
    User user = serviceBuilder().build();

    assertThatThrownBy(() -> user.linkLine("U-line-1"))
        .isInstanceOf(InvalidCredentialAssignmentException.class);
    assertThat(user.getLineUserId()).isNull();
  }

  @Test
  @DisplayName("SERVICE は少なくとも 1 つのロールが必要（STAFF と同じ不変条件）")
  void serviceWithoutRolesThrows() {
    assertThatThrownBy(() -> serviceBuilder().roleIds(Set.of()).build())
        .isInstanceOf(InvalidRoleGrantException.class);
  }

  @Test
  @DisplayName("SERVICE を停止しても行は残り、過去の実行主体の記録を保持する")
  void serviceStopKeepsTheRow() {
    User user = serviceBuilder().build();

    user.stop();

    assertThat(user.getEnabled()).isFalse();
    assertThat(user.getUserType()).isEqualTo(UserType.SERVICE);
    assertThat(user.getRoleIds()).containsExactly(1L);
  }

  @Test
  @DisplayName("STAFF は email なしで構築できない（放緩した列の守衛はドメイン不変条件だけ）")
  void staffWithoutEmailThrows() {
    assertThatThrownBy(() -> staffBuilder().email(null).build())
        .isInstanceOf(InvalidCredentialAssignmentException.class);
  }

  @Test
  @DisplayName("STAFF はパスワードなしで構築できない")
  void staffWithoutPasswordThrows() {
    assertThatThrownBy(() -> staffBuilder().password(null).build())
        .isInstanceOf(InvalidCredentialAssignmentException.class);
  }

  @Test
  @DisplayName("MEMBER も email・パスワードは必須（資格情報の免除は SERVICE だけ）")
  void memberWithoutCredentialsThrows() {
    assertThatThrownBy(
            () ->
                staffBuilder()
                    .userType(UserType.MEMBER)
                    .roleIds(Set.of())
                    .storeIds(Set.of())
                    .password(null)
                    .build())
        .isInstanceOf(InvalidCredentialAssignmentException.class);
  }

  @Test
  @DisplayName("退会済みにするのは一度だけで、二度目と日時無しは不変条件違反")
  void markWithdrawnIsTerminalAndRequiresTime() {
    User user = staffBuilder().build();
    assertThat(user.isWithdrawn()).isFalse();

    user.markWithdrawn(OffsetDateTime.parse("2026-10-26T10:00:00+09:00"));

    assertThat(user.isWithdrawn()).isTrue();
    assertThat(user.getWithdrawnAt()).isNotNull();
    assertThatThrownBy(() -> user.markWithdrawn(OffsetDateTime.now()))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> staffBuilder().build().markWithdrawn(null))
        .isInstanceOf(ServiceException.class);
  }
}
