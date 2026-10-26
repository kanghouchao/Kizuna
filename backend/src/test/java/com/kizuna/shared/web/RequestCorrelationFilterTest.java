package com.kizuna.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.shared.storescope.CurrentStoreArgumentResolver;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.shared.storescope.StoreScopeHolder;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestCorrelationFilterTest {
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void asyncExceptionDispatchKeepsResponseIdAndAlwaysClearsContext(boolean suppliedId)
      throws Exception {
    var holder = new StoreScopeHolder();
    var filter = new RequestCorrelationFilter(holder);
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();
    if (suppliedId) request.addHeader("X-Request-ID", "request-from-client");
    filter.doFilter(
        request,
        response,
        (input, output) ->
            assertThat(ThreadContext.get("requestId"))
                .isEqualTo(response.getHeader("X-Request-ID")));
    String originalId = response.getHeader("X-Request-ID");
    assertThat(originalId).isNotBlank();
    assertThat(ThreadContext.get("requestId")).isNull();
    request.setDispatcherType(DispatcherType.ASYNC);
    try {
      assertThatThrownBy(
              () ->
                  filter.doFilter(
                      request,
                      response,
                      (input, output) -> {
                        assertThat(ThreadContext.get("requestId")).isEqualTo(originalId);
                        ThreadContext.put(CurrentStoreArgumentResolver.MDC_STORE_ID, "1");
                        holder.install(() -> new StoreScope(true, java.util.Set.of()));
                        throw new ServletException("非同期失敗");
                      }))
          .isInstanceOf(ServletException.class);
      assertThat(response.getHeader("X-Request-ID")).isEqualTo(originalId);
      assertThat(ThreadContext.get("requestId")).isNull();
      assertThat(ThreadContext.get(CurrentStoreArgumentResolver.MDC_STORE_ID)).isNull();
      assertThat(holder.current()).as("作用域の保持器も例外経路で掃除されること").isNull();
    } finally {
      ThreadContext.clearAll();
      holder.clear();
    }
  }
}
