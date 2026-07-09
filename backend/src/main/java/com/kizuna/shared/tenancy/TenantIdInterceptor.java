package com.kizuna.shared.tenancy;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Log4j2
@Component
@AllArgsConstructor
public class TenantIdInterceptor implements HandlerInterceptor {

  private final TenantContext tenantContext;

  private static final String HEADER_ROLE = "X-Role";
  private static final String HEADER_TENANT_ID = "X-Tenant-ID";
  private static final String HEADER_ROLE_TENANT = "tenant";

  @Override
  public boolean preHandle(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull Object handler) {
    Long jwtTenantId = authenticatedTenantId();
    if (jwtTenantId != null) {
      // 認証済みテナント JWT: claim を正としてテナント文脈を確定する。ヘッダの有無・形式には依存させない
      // （X-Role/X-Tenant-ID を省略・改変してこの検証と分離を素通りされるのを防ぐため）。
      String headerTenantId = request.getHeader(HEADER_TENANT_ID);
      if (StringUtils.isNumeric(headerTenantId)
          && !jwtTenantId.equals(Long.parseLong(headerTenantId))) {
        // X-Tenant-ID が別テナントを指す明確な詐称は拒否する。
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return false;
      }
      this.tenantContext.setTenantId(jwtTenantId);
      return true;
    }
    // 認証前（ログイン等）または tenantId claim を持たないトークン: 従来通りヘッダのみで信用する。
    if (HEADER_ROLE_TENANT.equals(request.getHeader(HEADER_ROLE))
        && StringUtils.isNotBlank(request.getHeader(HEADER_TENANT_ID))
        && StringUtils.isNumeric(request.getHeader(HEADER_TENANT_ID))) {
      this.tenantContext.setTenantId(Long.parseLong(request.getHeader(HEADER_TENANT_ID)));
    }
    return true;
  }

  /**
   * 認証済みリクエストの JWT に埋め込まれた tenantId claim を返す。未認証、または details に {@link Claims}
   * を持たない（匿名認証やログイン前など）場合は null を返し、呼び出し側は従来通りヘッダのみで文脈を設定する。
   */
  private Long authenticatedTenantId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getDetails() instanceof Claims claims) {
      return claims.get("tenantId", Long.class);
    }
    return null;
  }

  @Override
  public void afterCompletion(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull Object handler,
      @Nullable Exception ex) {
    tenantContext.clear();
  }
}
