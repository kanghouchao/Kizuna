package com.kizuna.config.interceptor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TenantContextTest {

  @Test
  void contextWorks() {
    TenantContext context = new TenantContext();
    context.setTenantId(123L);
    assertThat(context.getTenantId()).isEqualTo(123L);

    context.clear();
    assertThat(context.getTenantId()).isNull();
  }
}
