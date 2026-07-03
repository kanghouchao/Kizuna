package com.kizuna.shared.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TenantIdInterceptorTest {

  private TenantContext tenantContext;
  private TenantIdInterceptor interceptor;

  @BeforeEach
  void setUp() {
    tenantContext = new TenantContext();
    interceptor = new TenantIdInterceptor(tenantContext);
  }

  @Test
  @DisplayName("X-Role が tenant かつ X-Tenant-ID が数値ならテナント文脈を設定すること")
  void preHandle_setsTenantIdForTenantRole() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Role", "tenant");
    request.addHeader("X-Tenant-ID", "42");

    boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

    assertThat(result).isTrue();
    assertThat(tenantContext.getTenantId()).isEqualTo(42L);
  }

  @Test
  @DisplayName("X-Role が tenant でなければテナント文脈を設定しないこと")
  void preHandle_ignoresNonTenantRole() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Role", "central");
    request.addHeader("X-Tenant-ID", "42");

    boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

    assertThat(result).isTrue();
    assertThat(tenantContext.isTenant()).isFalse();
  }

  @Test
  @DisplayName("X-Tenant-ID が数値でなければテナント文脈を設定しないこと")
  void preHandle_ignoresNonNumericTenantId() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Role", "tenant");
    request.addHeader("X-Tenant-ID", "abc");

    boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

    assertThat(result).isTrue();
    assertThat(tenantContext.isTenant()).isFalse();
  }

  @Test
  @DisplayName("afterCompletion でテナント文脈がクリアされること")
  void afterCompletion_clearsTenantContext() {
    tenantContext.setTenantId(42L);

    interceptor.afterCompletion(
        new MockHttpServletRequest(), new MockHttpServletResponse(), new Object(), null);

    assertThat(tenantContext.isTenant()).isFalse();
  }
}
