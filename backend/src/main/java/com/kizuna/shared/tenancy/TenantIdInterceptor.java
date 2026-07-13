package com.kizuna.shared.tenancy;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Log4j2
@Component
@AllArgsConstructor
public class TenantIdInterceptor implements HandlerInterceptor {

  private final TenantContext tenantContext;

  private static final String HEADER_ROLE = "X-Role";
  private static final String HEADER_TENANT_ID = "X-Tenant-ID";
  private static final String HEADER_ROLE_TENANT = "tenant";

  /**
   * 平台トークンで店舗文脈（X-Tenant-ID）を確立できる PlatformRole の role claim 値。 shared 層は user モジュールへ依存しないため enum
   * ではなく文字列で保持する（StoreScope の scopeType 判定と同方針）。
   */
  private static final Set<String> STORE_BRIDGE_ROLES = Set.of("STORE_MANAGER", "STORE_STAFF");

  @Override
  public boolean preHandle(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull Object handler) {
    Claims claims = authenticatedClaims();
    Long jwtTenantId = claims == null ? null : claims.get("tenantId", Long.class);
    if (jwtTenantId != null) {
      // 認証済みテナント JWT: claim を正としてテナント文脈を確定する。ヘッダの有無・形式には依存させない
      // （X-Role/X-Tenant-ID を省略・改変してこの検証と分離を素通りされるのを防ぐため）。
      String headerTenantId = request.getHeader(HEADER_TENANT_ID);
      if (StringUtils.isNumeric(headerTenantId)) {
        Long headerValue = tryParseTenantId(headerTenantId);
        if (headerValue == null) {
          // 全桁数字だが long 範囲外。素の 500 を避けクリーンに 400 で拒否する（#288）。
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          return false;
        }
        if (!jwtTenantId.equals(headerValue)) {
          // X-Tenant-ID が別テナントを指す明確な詐称は拒否する。
          response.setStatus(HttpServletResponse.SC_FORBIDDEN);
          return false;
        }
      }
      this.tenantContext.setTenantId(jwtTenantId);
      return true;
    }
    StoreScope scope =
        StoreScope.fromAuthentication(SecurityContextHolder.getContext().getAuthentication());
    boolean claimsTenantHeaderPresent =
        HEADER_ROLE_TENANT.equals(request.getHeader(HEADER_ROLE))
            && StringUtils.isNotBlank(request.getHeader(HEADER_TENANT_ID));
    if (scope != null) {
      // 平台トークン（#324 過橋）: X-Role: tenant + 数値 X-Tenant-ID を要求し、授権店舗集合で検証する（fail-closed）。
      if (HEADER_ROLE_TENANT.equals(request.getHeader(HEADER_ROLE))
          && StringUtils.isNumeric(request.getHeader(HEADER_TENANT_ID))) {
        // 店舗文脈を名乗れるのは店舗ロール（店長・スタッフ）のみ。CAST/MEMBER/HQ_ADMIN が店舗ヘッダを
        // 名乗るのは詐称として 403 で拒否する（#294 と同型）。授権スコープ検証より前に弾き、
        // isAuthenticated() のみの端点（/files/upload 等）への店舗文脈確立の漏れを塞ぐ。
        if (!STORE_BRIDGE_ROLES.contains(claims.get("role", String.class))) {
          response.setStatus(HttpServletResponse.SC_FORBIDDEN);
          return false;
        }
        Long headerValue = tryParseTenantId(request.getHeader(HEADER_TENANT_ID));
        if (headerValue == null) {
          // 全桁数字だが long 範囲外（#288 と同型）。
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          return false;
        }
        if (!scope.authorizes(headerValue)) {
          response.setStatus(HttpServletResponse.SC_FORBIDDEN);
          return false;
        }
        this.tenantContext.setTenantId(headerValue);
        return true;
      }
      // ヘッダ不備（X-Role 欠落・非数値含む）は店舗文脈なし → 末尾の @TenantOptional 判定へ落とす。
    } else if (claims != null) {
      // 認証済みだが tenantId claim を持たない（央端 CentralAuth 発行、または tenantId 無しの legacy）。
      // ヘッダでテナントを名乗る主張は詐称として 403 で拒否する（#294）。詐称ヘッダが無ければ下の
      // @TenantOptional 判定に委ね、央端の正当な素通り（例: /files/upload の central 保存）を保つ。
      if (claimsTenantHeaderPresent) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return false;
      }
    } else if (claimsTenantHeaderPresent
        && StringUtils.isNumeric(request.getHeader(HEADER_TENANT_ID))) {
      // 未認証（ログイン前・公開ページ）だけがヘッダのみでテナントを名乗れる。
      Long headerValue = tryParseTenantId(request.getHeader(HEADER_TENANT_ID));
      if (headerValue == null) {
        // 全桁数字だが long 範囲外（#288）。
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return false;
      }
      this.tenantContext.setTenantId(headerValue);
      return true;
    }
    // JWT claim・ヘッダのいずれからもテナント文脈を解決できなかった。文脈が無いまま素通りさせると
    // tenantFilter が有効化されず @TenantScoped クエリが全テナントの行を返す（fail-open）ため、
    // @TenantOptional を明示したエンドポイントに限り許可し、それ以外は 403 で拒否する（fail-closed）。
    if (handler instanceof HandlerMethod handlerMethod
        && handlerMethod.hasMethodAnnotation(TenantOptional.class)) {
      return true;
    }
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    return false;
  }

  /**
   * 認証済みリクエストの JWT Claims（JwtAuthenticationFilter が details に載せたもの）を返す。 未認証、または details に {@link
   * Claims} を持たない（匿名認証やログイン前など）場合は null。
   */
  private Claims authenticatedClaims() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getDetails() instanceof Claims claims) {
      return claims;
    }
    return null;
  }

  /** 全桁数字の文字列を long に変換する。long 範囲を超える場合は null（呼び出し側が 400 を返す）。 */
  private static Long tryParseTenantId(String numericValue) {
    try {
      return Long.parseLong(numericValue);
    } catch (NumberFormatException e) {
      return null;
    }
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
