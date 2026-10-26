package com.kizuna.shared.web;

import com.kizuna.shared.storescope.CurrentStoreArgumentResolver;
import com.kizuna.shared.storescope.StoreScopeHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.ThreadContext;
import org.jspecify.annotations.NonNull;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/** 全リクエストに相関 ID を割り当て、応答後に ThreadContext と店舗作用域の保持器を確実に掃除する。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class RequestCorrelationFilter extends OncePerRequestFilter {

  private static final String REQUEST_ID_HEADER = "X-Request-ID";
  private static final String REQUEST_ID_ATTRIBUTE =
      RequestCorrelationFilter.class.getName() + ".id";

  private final StoreScopeHolder storeScopeHolder;

  @Override
  protected boolean shouldNotFilterAsyncDispatch() {
    return false;
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    String requestId = (String) request.getAttribute(REQUEST_ID_ATTRIBUTE);
    if (requestId == null) {
      requestId = request.getHeader(REQUEST_ID_HEADER);
      if (!StringUtils.hasText(requestId)) requestId = UUID.randomUUID().toString();
      request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
    }
    ThreadContext.put("requestId", requestId);
    response.setHeader(REQUEST_ID_HEADER, requestId);

    try {
      filterChain.doFilter(request, response);
    } finally {
      ThreadContext.remove("requestId");
      ThreadContext.remove(CurrentStoreArgumentResolver.MDC_STORE_ID);
      // 非同期処理の初回パスではインターセプタの afterCompletion が呼ばれないため、ここが唯一の掃除口になる。
      storeScopeHolder.clear();
    }
  }
}
