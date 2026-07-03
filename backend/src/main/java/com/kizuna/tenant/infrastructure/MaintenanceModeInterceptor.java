package com.kizuna.tenant.infrastructure;

import com.kizuna.service.central.config.SystemConfigService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** メンテナンスモード中にテナント向けリクエストを 503 で拒否するインターセプタ。 */
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
    response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getWriter().write("{\"error\":\"メンテナンス中です。しばらくしてから再度お試しください\"}");
    return false;
  }
}
