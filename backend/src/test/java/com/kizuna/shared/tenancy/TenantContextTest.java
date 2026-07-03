package com.kizuna.shared.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TenantContextTest {

  @Test
  @DisplayName("テナント ID を設定すると isTenant が true になり取得できること")
  void setAndGetTenantId() {
    TenantContext context = new TenantContext();
    assertThat(context.isTenant()).isFalse();
    assertThat(context.getTenantId()).isNull();

    context.setTenantId(42L);

    assertThat(context.isTenant()).isTrue();
    assertThat(context.getTenantId()).isEqualTo(42L);
  }

  @Test
  @DisplayName("clear するとテナント文脈が消えること")
  void clear_removesTenantId() {
    TenantContext context = new TenantContext();
    context.setTenantId(42L);

    context.clear();

    assertThat(context.isTenant()).isFalse();
    assertThat(context.getTenantId()).isNull();
  }
}
