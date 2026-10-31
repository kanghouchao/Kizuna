package com.kizuna.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.shared.config.AppProperties;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RefreshTokenCookiesTest {

  private static RefreshTokenCookies cookies(AppProperties.Scheme scheme) {
    AppProperties properties = new AppProperties();
    properties.setScheme(scheme);
    return new RefreshTokenCookies(properties);
  }

  @Test
  void secureOnlyUnderHttps() {
    IssuedJwt refresh = new IssuedJwt("t", Instant.now().plusSeconds(600));

    assertThat(cookies(AppProperties.Scheme.HTTPS).issue(refresh).isSecure()).isTrue();
    assertThat(cookies(AppProperties.Scheme.HTTPS).clear().isSecure()).isTrue();
    assertThat(cookies(AppProperties.Scheme.HTTP).issue(refresh).isSecure()).isFalse();
  }

  @Test
  void maxAgeIsTheRemainingLifetime() {
    var cookie =
        cookies(AppProperties.Scheme.HTTP)
            .issue(new IssuedJwt("t", Instant.now().plusSeconds(600)));

    assertThat(cookie.getMaxAge().toSeconds()).isBetween(595L, 600L);
    assertThat(cookie.isHttpOnly()).isTrue();
    assertThat(cookie.getSameSite()).isEqualTo("Strict");
    assertThat(cookie.getPath()).isEqualTo("/api/auth");
    assertThat(cookie.getDomain()).isNull();
  }
}
