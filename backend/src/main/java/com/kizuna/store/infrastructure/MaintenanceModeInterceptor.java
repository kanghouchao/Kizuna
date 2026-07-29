package com.kizuna.store.infrastructure;

import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.exception.ServiceUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * メンテナンスモード中に店舗向けリクエストを 503 で拒否するインターセプタ。
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
}
