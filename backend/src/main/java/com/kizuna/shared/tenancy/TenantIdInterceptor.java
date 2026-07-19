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
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Log4j2
@Component
@AllArgsConstructor
public class TenantIdInterceptor implements HandlerInterceptor {

  private final TenantContext tenantContext;

  private static final String HEADER_ROLE = "X-Role";
  private static final String HEADER_TENANT_ID = "X-Store-ID";
  private static final String HEADER_ROLE_TENANT = "store";

  /**
   * 平台トークンで店舗文脈（X-Store-ID）を確立できるかを示す claim 名。値はログイン時に STORE コンソール能力の保持から導出される（#398 —
   * PlatformAuthService）。 shared 層は user モジュールへ依存しないため能力目録を参照せず、導出済みの boolean claim だけを消費する。
   */
  private static final String CLAIM_STORE_BRIDGE = "storeBridge";

  @Override
  public boolean preHandle(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull Object handler) {
    Claims claims = authenticatedClaims();
    StoreScope scope =
        StoreScope.fromAuthentication(SecurityContextHolder.getContext().getAuthentication());
    boolean claimsTenantHeaderPresent =
        HEADER_ROLE_TENANT.equals(request.getHeader(HEADER_ROLE))
            && StringUtils.isNotBlank(request.getHeader(HEADER_TENANT_ID));
    if (scope != null) {
      // 平台トークン（#324 過橋）: X-Role: store + 数値 X-Store-ID を要求し、授権店舗集合で検証する（fail-closed）。
      if (HEADER_ROLE_TENANT.equals(request.getHeader(HEADER_ROLE))
          && StringUtils.isNumeric(request.getHeader(HEADER_TENANT_ID))) {
        // 店舗文脈を名乗れるのは STORE コンソール能力の保持者（storeBridge claim）のみ。CAST/MEMBER/HQ が
        // 店舗ヘッダを名乗るのは詐称として 403 で拒否する（#294 と同型）。授権スコープ検証より前に弾き、
        // isAuthenticated() のみの端点（/files/upload 等）への店舗文脈確立の漏れを塞ぐ。
        if (!Boolean.TRUE.equals(claims.get(CLAIM_STORE_BRIDGE, Boolean.class))) {
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
