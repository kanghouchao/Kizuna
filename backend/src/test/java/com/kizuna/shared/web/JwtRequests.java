package com.kizuna.shared.web;

import java.util.Arrays;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** controller のテストが要求へ載せる、検証済み Bearer トークンの認証。 */
public final class JwtRequests {

  private JwtRequests() {}

  /**
   * 利用者 ID と権限を持つ認証。MVC 単体の文脈には security filter が無いので、認可（SecurityContext）と handler の Principal
   * の両方へ同じ認証を置く。文脈は Spring Security のテスト用 listener がテストごとに消す。
   */
  public static RequestPostProcessor jwtUser(long userId, String... authorities) {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("user-" + userId + "@example.com")
            .claim(JwtClaims.USER_ID, userId)
            .build();
    var authentication =
        new JwtAuthenticationToken(
            jwt, Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    return request -> {
      TestSecurityContextHolder.setAuthentication(authentication);
      request.setUserPrincipal(authentication);
      return request;
    };
  }
}
