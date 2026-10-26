package com.kizuna.store.infrastructure;

import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.exception.ServiceUnavailableException;
import com.kizuna.shared.storescope.CurrentStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * メンテナンスモード中に、現在店舗を必須とする handler（{@code @CurrentStore} が required）への要求を 503 で拒否する。引数解決より先に動くため、503
 * は {@code X-Store-ID} の検証（400・403）より先に返る。
 *
 * <p>応答の成形は {@link ServiceUnavailableException} を送出して {@link
 * com.kizuna.shared.exception.CommonExceptionHandler} へ委ね、ここでは response へ直接書かない。
 */
@Component
@RequiredArgsConstructor
public class MaintenanceModeInterceptor implements HandlerInterceptor {

  private static final String CONFIG_KEY_MAINTENANCE = "maintenance_mode";

  private final SystemConfigService systemConfigService;

  @Override
  public boolean preHandle(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull Object handler)
      throws Exception {
    if (!requiresStore(handler)) {
      return true;
    }
    boolean maintenance =
        systemConfigService
            .getConfigValue(CONFIG_KEY_MAINTENANCE)
            .map(Boolean::parseBoolean)
            .orElse(false);
    if (!maintenance) {
      return true;
    }
    throw new ServiceUnavailableException("メンテナンス中です。しばらくしてから再度お試しください");
  }

  private static boolean requiresStore(Object handler) {
    if (!(handler instanceof HandlerMethod handlerMethod)) {
      return false;
    }
    return Arrays.stream(handlerMethod.getMethodParameters())
        .map(parameter -> parameter.getParameterAnnotation(CurrentStore.class))
        .anyMatch(currentStore -> currentStore != null && currentStore.required());
  }
}
