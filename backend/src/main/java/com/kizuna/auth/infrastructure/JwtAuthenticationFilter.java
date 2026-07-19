package com.kizuna.auth.infrastructure;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.lang.Collections;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.lang.NonNull;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Log4j2
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtUtil jwtUtil;
  private final TokenBlacklistService tokenBlacklistService;

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    String authHeader = request.getHeader("Authorization");
    if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
      String token = authHeader.substring(7);
      Claims claims = parseClaims(token);
      if (claims != null
          && issuerMatchesDomain(claims.getIssuer(), request.getRequestURI())
          && !tokenBlacklistService.isBlacklisted(token)) {
        if (new Date().before(claims.getExpiration())) {
          String username = claims.getSubject();
          List<?> authorities = claims.get("authorities", List.class);
          if (!Collections.isEmpty(authorities)) {
            PreAuthenticatedAuthenticationToken authentication =
                new PreAuthenticatedAuthenticationToken(
                    username,
                    token,
                    authorities.stream()
                        .map(a -> new SimpleGrantedAuthority(String.valueOf(a)))
                        .toList());
            authentication.setAuthenticated(true);
            authentication.setDetails(claims);
            SecurityContextHolder.getContext().setAuthentication(authentication);
          }
        }
      }
    }
    filterChain.doFilter(request, response);
  }

  /** 期限切れ・改ざん等で解析できないトークンは「未認証」として扱う（500 にしない）。 */
  private Claims parseClaims(String token) {
    try {
      return jwtUtil.getClaims(token);
    } catch (Exception e) {
      log.debug("JWT の解析に失敗（未認証として続行）: {}", e.getMessage());
      return null;
    }
  }

  /**
   * リクエストパスの属するドメインとトークンの issuer が一致するか検証する。 統一（プラットフォーム）認証への一本化後、業務ドメイン（/store・/platform）は いずれも
   * PlatformAuth 発行のトークンのみを受理する。ドメイン外のパス（/files 等）は制限しない。 店舗文脈の授権検証は
   * TenantIdInterceptor（StoreScope）が担う。
   */
  private boolean issuerMatchesDomain(String issuer, String path) {
    boolean restrictedDomain =
        path.equals("/store")
            || path.startsWith("/store/")
            || path.equals("/platform")
            || path.startsWith("/platform/");
    return !restrictedDomain || JwtUtil.ISSUER_PLATFORM.equals(issuer);
  }
}
