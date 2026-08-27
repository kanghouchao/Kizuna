package com.kizuna.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PermissionCodeTest {

  @Test
  @DisplayName("authority は Authorities.permission と同一の PERM_ 接頭辞表現を返す")
  void authorityUsesPermissionEncoding() {
    assertThat(PermissionCode.ORDER_MANAGE.authority()).isEqualTo("PERM_ORDER_MANAGE");
    assertThat(PermissionCode.STORE_MANAGE.authority())
        .isEqualTo(Authorities.permission(PermissionCode.STORE_MANAGE.name()));
  }

  @Test
  @DisplayName("PLATFORM コンソールの権限はプラットフォーム管理系の 6 個")
  void platformPermissions() {
    assertThat(byConsole(PermissionCode.Console.PLATFORM))
        .containsExactlyInAnyOrder(
            PermissionCode.STORE_MANAGE,
            PermissionCode.ROLE_MANAGE,
            PermissionCode.STAFF_ACCOUNT_MANAGE,
            PermissionCode.SYSTEM_CONFIG_MANAGE,
            PermissionCode.PLATFORM_MENU_VIEW,
            PermissionCode.PLATFORM_ASSET_MANAGE);
  }

  @Test
  @DisplayName("SHARED コンソールの権限は跨店参照系の 2 個")
  void sharedPermissions() {
    assertThat(byConsole(PermissionCode.Console.SHARED))
        .containsExactlyInAnyOrder(PermissionCode.STORE_VIEW, PermissionCode.ORDER_SET_MANAGE);
  }

  @Test
  @DisplayName("STORE コンソールの権限は店舗業務系の 13 個")
  void storePermissions() {
    assertThat(byConsole(PermissionCode.Console.STORE))
        .containsExactlyInAnyOrder(
            PermissionCode.STAFF_MANAGE,
            PermissionCode.ORDER_MANAGE,
            PermissionCode.CUSTOMER_MANAGE,
            PermissionCode.CUSTOMER_MERGE,
            PermissionCode.ORDER_CORRECT,
            PermissionCode.POINT_ADJUST,
            PermissionCode.SHIFT_MANAGE,
            PermissionCode.CAST_MANAGE,
            PermissionCode.CAST_INVITE,
            PermissionCode.CAST_FIELD_DEF_VIEW,
            PermissionCode.CAST_FIELD_DEF_MANAGE,
            PermissionCode.STORE_PROFILE_MANAGE,
            PermissionCode.STORE_MENU_VIEW);
  }

  @Test
  @DisplayName("店舗コンソールの入場資格は STORE の権限から標識権限を除いたもの")
  void storeConsoleCodesExcludeTheMenuMarkerAndSharedPermissions() {
    // 着地の判定と付与時の検証が共有する述語の定義そのもの。標識権限だけ、あるいは SHARED だけのロールを
    // 資格ありに数えると、作成はできるのにログイン後どこへも着地できないアカウントが通ってしまう。
    assertThat(PermissionCode.STORE_MENU_VIEW.grantsStoreConsole()).isFalse();
    assertThat(PermissionCode.STORE_VIEW.grantsStoreConsole()).isFalse();
    assertThat(PermissionCode.ORDER_MANAGE.grantsStoreConsole()).isTrue();
    assertThat(PermissionCode.storeConsoleCodes())
        .contains(PermissionCode.ORDER_MANAGE.name(), PermissionCode.STAFF_MANAGE.name())
        .doesNotContain(
            PermissionCode.STORE_MENU_VIEW.name(),
            PermissionCode.STORE_VIEW.name(),
            PermissionCode.ORDER_SET_MANAGE.name(),
            PermissionCode.ROLE_MANAGE.name());
  }

  @Test
  @DisplayName("権限目録は 21 個で全てコンソール分類を持つ")
  void catalogIsComplete() {
    assertThat(PermissionCode.values()).hasSize(21);
    assertThat(Arrays.stream(PermissionCode.values()).map(PermissionCode::getConsole))
        .doesNotContainNull();
  }

  private Set<PermissionCode> byConsole(PermissionCode.Console console) {
    return Arrays.stream(PermissionCode.values())
        .filter(permission -> permission.getConsole() == console)
        .collect(Collectors.toSet());
  }
}
