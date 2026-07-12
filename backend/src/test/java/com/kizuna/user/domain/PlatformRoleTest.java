package com.kizuna.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlatformRoleTest {

  @Test
  @DisplayName("HQ_ADMIN は央端業務権限を持つ")
  void hqAdminGrantsCentralPermissions() {
    assertThat(PlatformRole.HQ_ADMIN.grantedPermissions())
        .containsExactlyInAnyOrder("TENANT_MANAGE", "SYSTEM_CONFIG");
  }

  @Test
  @DisplayName("STORE_MANAGER と STORE_STAFF は同一の店舗業務権限を持つ")
  void storeRolesGrantSameStorePermissions() {
    Set<String> expected =
        Set.of("ORDER_MANAGE", "CAST_MANAGE", "CUSTOMER_MANAGE", "TENANT_CONFIG");
    assertThat(PlatformRole.STORE_MANAGER.grantedPermissions())
        .containsExactlyInAnyOrderElementsOf(expected);
    assertThat(PlatformRole.STORE_STAFF.grantedPermissions())
        .containsExactlyInAnyOrderElementsOf(expected);
  }

  @Test
  @DisplayName("CAST と MEMBER は旧業務権限を持たない")
  void castAndMemberGrantNoPermissions() {
    assertThat(PlatformRole.CAST.grantedPermissions()).isEmpty();
    assertThat(PlatformRole.MEMBER.grantedPermissions()).isEmpty();
  }
}
