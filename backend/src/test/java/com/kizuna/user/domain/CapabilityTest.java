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
    assertThat(Capability.TENANT_MANAGE.authority())
        .isEqualTo(Authorities.permission(Capability.TENANT_MANAGE.name()));
  }

  @Test
  @DisplayName("CENTRAL コンソールの能力は中央管理系の 5 個")
  void centralCapabilities() {
    assertThat(byConsole(Capability.Console.CENTRAL))
        .containsExactlyInAnyOrder(
            Capability.TENANT_MANAGE,
            Capability.STAFF_MANAGE,
            Capability.SYSTEM_CONFIG_MANAGE,
            Capability.CENTRAL_MENU_VIEW,
            Capability.CENTRAL_ASSET_MANAGE);
  }

  @Test
  @DisplayName("SHARED コンソールの能力は跨店参照系の 2 個")
  void sharedCapabilities() {
    assertThat(byConsole(Capability.Console.SHARED))
        .containsExactlyInAnyOrder(Capability.STORE_VIEW, Capability.ORDER_SET_MANAGE);
  }

  @Test
  @DisplayName("STORE コンソールの能力は店舗業務系の 8 個")
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
            Capability.STORE_PROFILE_MANAGE);
  }

  @Test
  @DisplayName("能力目録は既存端点に対応する 15 個で全てコンソール分類を持つ")
  void catalogIsComplete() {
    assertThat(Capability.values()).hasSize(15);
    assertThat(Arrays.stream(Capability.values()).map(Capability::getConsole)).doesNotContainNull();
  }

  private Set<Capability> byConsole(Capability.Console console) {
    return Arrays.stream(Capability.values())
        .filter(capability -> capability.getConsole() == console)
        .collect(Collectors.toSet());
  }
}
