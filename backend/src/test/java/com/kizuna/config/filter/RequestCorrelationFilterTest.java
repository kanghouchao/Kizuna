package com.kizuna.config.filter;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.kizuna.config.interceptor.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

class RequestCorrelationFilterTest {

  @Test
  void testFilter() throws Exception {
    TenantContext tenantContext = mock(TenantContext.class);
    RequestCorrelationFilter filter = new RequestCorrelationFilter(tenantContext);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verify(response)
        .setHeader(
            org.mockito.ArgumentMatchers.eq("X-Request-ID"),
            org.mockito.ArgumentMatchers.anyString());
  }
}
