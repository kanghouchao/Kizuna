package com.kizuna.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CapabilityTest {

  @Test
  @DisplayName("authority は Authorities.permission と同一の PERM_ 接頭辞表現を返す")
  void authorityUsesPermissionEncoding() {
    assertThat(Capability.ORDER_MANAGE.authority()).isEqualTo("PERM_ORDER_MANAGE");
    assertThat(Capability.STORE_MANAGE.authority())
        .isEqualTo(Authorities.permission(Capability.STORE_MANAGE.name()));
  }

  @Test
  @DisplayName("PLATFORM コンソールの能力はプラットフォーム管理系の 5 個")
  void platformCapabilities() {
    assertThat(byConsole(Capability.Console.PLATFORM))
        .containsExactlyInAnyOrder(
            Capability.STORE_MANAGE,
            Capability.STAFF_MANAGE,
            Capability.SYSTEM_CONFIG_MANAGE,
            Capability.PLATFORM_MENU_VIEW,
            Capability.PLATFORM_ASSET_MANAGE);
  }

  @Test
  @DisplayName("SHARED コンソールの能力は跨店参照系の 2 個")
  void sharedCapabilities() {
    assertThat(byConsole(Capability.Console.SHARED))
        .containsExactlyInAnyOrder(Capability.STORE_VIEW, Capability.ORDER_SET_MANAGE);
  }

  @Test
  @DisplayName("STORE コンソールの能力は店舗業務系の 9 個")
  void storeCapabilities() {
    assertThat(byConsole(Capability.Console.STORE))
        .containsExactlyInAnyOrder(
            Capability.ORDER_MANAGE,
            Capability.CUSTOMER_MANAGE,
            Capability.SHIFT_MANAGE,
            Capability.CAST_MANAGE,
            Capability.CAST_INVITE,
            Capability.CAST_FIELD_DEF_VIEW,
            Capability.CAST_FIELD_DEF_MANAGE,
            Capability.STORE_PROFILE_MANAGE,
            Capability.STORE_MENU_VIEW);
  }

  @Test
  @DisplayName("能力目録は 16 個で全てコンソール分類を持つ")
  void catalogIsComplete() {
    assertThat(Capability.values()).hasSize(16);
    assertThat(Arrays.stream(Capability.values()).map(Capability::getConsole)).doesNotContainNull();
  }

  private Set<Capability> byConsole(Capability.Console console) {
    return Arrays.stream(Capability.values())
        .filter(capability -> capability.getConsole() == console)
        .collect(Collectors.toSet());
  }
}
