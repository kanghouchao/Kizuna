package com.cms.config.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cms.config.interceptor.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@SuppressWarnings("null")
class RequestCorrelationFilterTest {

  @Test
  void filter_addsRequestId_whenMissing() throws Exception {
    TenantContext tenantContext = mock(TenantContext.class);
    RequestCorrelationFilter filter = new RequestCorrelationFilter(tenantContext);

    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader("X-Request-ID")).thenReturn(null);

    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    filter.doFilterInternal(request, response, chain);

    ArgumentCaptor<String> headerValueCaptor = ArgumentCaptor.forClass(String.class);
    verify(response).setHeader(eq("X-Request-ID"), headerValueCaptor.capture());

    assertThat(headerValueCaptor.getValue()).isNotEmpty();
    verify(tenantContext).clear();
    verify(chain).doFilter(request, response);
  }

  @Test
  void filter_usesExistingRequestId() throws Exception {
    TenantContext tenantContext = mock(TenantContext.class);
    RequestCorrelationFilter filter = new RequestCorrelationFilter(tenantContext);

    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader("X-Request-ID")).thenReturn("existing-id");

    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    filter.doFilterInternal(request, response, chain);

    verify(response).setHeader("X-Request-ID", "existing-id");
    verify(tenantContext).clear();
    verify(chain).doFilter(request, response);
  }
}
