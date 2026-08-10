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
  @DisplayName("PLATFORM コンソールの権限はプラットフォーム管理系の 5 個")
  void platformPermissions() {
    assertThat(byConsole(PermissionCode.Console.PLATFORM))
        .containsExactlyInAnyOrder(
            PermissionCode.STORE_MANAGE,
            PermissionCode.STAFF_MANAGE,
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
  @DisplayName("STORE コンソールの権限は店舗業務系の 10 個")
  void storePermissions() {
    assertThat(byConsole(PermissionCode.Console.STORE))
        .containsExactlyInAnyOrder(
            PermissionCode.ORDER_MANAGE,
            PermissionCode.CUSTOMER_MANAGE,
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
  @DisplayName("権限目録は 17 個で全てコンソール分類を持つ")
  void catalogIsComplete() {
    assertThat(PermissionCode.values()).hasSize(17);
    assertThat(Arrays.stream(PermissionCode.values()).map(PermissionCode::getConsole))
        .doesNotContainNull();
  }

  private Set<PermissionCode> byConsole(PermissionCode.Console console) {
    return Arrays.stream(PermissionCode.values())
        .filter(permission -> permission.getConsole() == console)
        .collect(Collectors.toSet());
  }
}
