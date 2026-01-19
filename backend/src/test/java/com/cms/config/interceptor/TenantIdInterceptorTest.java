package com.cms.config.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class TenantIdInterceptorTest {

  @Mock TenantContext tenantContext;

  @InjectMocks TenantIdInterceptor interceptor;

  @Test
  void preHandle_setsTenantId_whenHeadersPresentAndValid() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    Object handler = new Object();

    when(request.getHeader("X-Role")).thenReturn("tenant");
    when(request.getHeader("X-Tenant-ID")).thenReturn("123");

    boolean result = interceptor.preHandle(request, response, handler);

    assertThat(result).isTrue();
    verify(tenantContext).setTenantId(123L);
  }

  @Test
  void preHandle_doesNotSetTenantId_whenRoleHeaderMissing() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    Object handler = new Object();

    when(request.getHeader("X-Role")).thenReturn(null);

    boolean result = interceptor.preHandle(request, response, handler);

    assertThat(result).isTrue();
    verify(tenantContext, never()).setTenantId(any(Long.class));
  }

  @Test
  void preHandle_doesNotSetTenantId_whenTenantIdHeaderMissing() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    Object handler = new Object();

    when(request.getHeader("X-Role")).thenReturn("tenant");
    when(request.getHeader("X-Tenant-ID")).thenReturn(null); // Missing X-Tenant-ID header

    boolean result = interceptor.preHandle(request, response, handler);

    assertThat(result).isTrue();
    verify(tenantContext, never()).setTenantId(any(Long.class));
  }

  @Test
  void preHandle_doesNotSetTenantId_whenTenantIdNotNumeric() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    Object handler = new Object();

    when(request.getHeader("X-Role")).thenReturn("tenant");
    when(request.getHeader("X-Tenant-ID")).thenReturn("abc"); // Non-numeric Tenant ID

    boolean result = interceptor.preHandle(request, response, handler);

    assertThat(result).isTrue();
    verify(tenantContext, never()).setTenantId(any(Long.class));
  }

  @Test
  void afterCompletion_clearsTenantContext() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    Object handler = new Object();

    interceptor.afterCompletion(request, response, handler, null);

    verify(tenantContext).clear();
  }
}
