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
import org.springframework.data.redis.core.RedisTemplate;
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
  private final RedisTemplate<String, Object> redisTemplate;

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    String authHeader = request.getHeader("Authorization");
    if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
      String token = authHeader.substring(7);
      Claims claims = jwtUtil.getClaims(token);
      if (issuerMatchesDomain(claims.getIssuer(), request.getRequestURI())
          && !redisTemplate.hasKey("blacklist:tokens:" + token)) {
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

  /**
   * リクエストパスの属するドメインとトークンの issuer が一致するか検証する。 Central / Tenant は署名キーを共有しているため、issuer
   * の照合でドメイン間のトークン流用を防ぐ。ドメイン外のパス（/files 等）は制限しない。
   */
  private boolean issuerMatchesDomain(String issuer, String path) {
    if (path.equals("/central") || path.startsWith("/central/")) {
      return JwtUtil.ISSUER_CENTRAL.equals(issuer);
    }
    if (path.equals("/tenant") || path.startsWith("/tenant/")) {
      return JwtUtil.ISSUER_TENANT.equals(issuer);
    }
    return true;
  }
}
