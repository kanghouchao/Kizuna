package com.kizuna.shared.storescope;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * {@link Authentication} から Spring の {@link Jwt}（resource-server が構築した claim 集合）を取り出す唯一の窓口。
 *
 * <p>{@link StoreScope#fromAuthentication} / {@link StoreBridgeGuard#check} / {@link
 * StoreIdInterceptor#authenticatedClaims} の 3 箇所が同じ authentication から独立に claim を読むと、判定条件が
 * 食い違うおそれがある（例: 片方だけ非 {@link JwtAuthenticationToken} を許してしまう）。抽出をここへ一本化することで、3 箇所は常に同じ
 * authentication から同じ結果（null かどうかを含めて）を得る。
 *
 * <p>{@code JwtAuthenticationToken} でない（未認証・匿名等）場合は null を返し、呼び出し側が fail-closed に扱う。
 */
final class AuthenticatedJwt {

  private AuthenticatedJwt() {}

  static Jwt from(Authentication authentication) {
    if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
      return jwtAuthentication.getToken();
    }
    return null;
  }
}
