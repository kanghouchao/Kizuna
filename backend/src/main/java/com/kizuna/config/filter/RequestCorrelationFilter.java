package com.kizuna.config.filter;

import com.kizuna.config.interceptor.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Assigns a correlation identifier to every request and ensures tenant context data exits cleanly.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

  private static final String REQUEST_ID_HEADER = "X-Request-ID";

  private final TenantContext tenantContext;

  public RequestCorrelationFilter(TenantContext tenantContext) {
    this.tenantContext = tenantContext;
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    String requestId = request.getHeader(REQUEST_ID_HEADER);
    if (!StringUtils.hasText(requestId)) {
      requestId = UUID.randomUUID().toString();
    }
    ThreadContext.put("requestId", requestId);
    response.setHeader(REQUEST_ID_HEADER, requestId);

    try {
      filterChain.doFilter(request, response);
    } finally {
      ThreadContext.remove("requestId");
      ThreadContext.remove("tenantId");
      tenantContext.clear();
    }
  }
}
