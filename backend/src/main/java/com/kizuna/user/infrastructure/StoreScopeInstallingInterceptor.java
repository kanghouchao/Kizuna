package com.kizuna.user.infrastructure;

import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.user.application.StoreScopeResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 認証済み要求に店舗作用域の解決器を {@link StoreScopeHolder} へ差し込む。全パスで {@code StoreIdInterceptor} より先に動き、
 * 解決は保持器が最初に読まれた時まで遅延する（作用域を見ない要求では DB を読まない）。afterCompletion で必ず消す。
 */
@Component
@RequiredArgsConstructor
public class StoreScopeInstallingInterceptor implements HandlerInterceptor {

  private final StoreScopeHolder storeScopeHolder;
  private final StoreScopeResolver storeScopeResolver;

  @Override
  public boolean preHandle(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull Object handler) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken jwt && jwt.isAuthenticated()) {
      storeScopeHolder.install(() -> storeScopeResolver.resolve(jwt.getToken()));
    }
    return true;
  }

  @Override
  public void afterCompletion(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull Object handler,
      @Nullable Exception ex) {
    storeScopeHolder.clear();
  }
}
