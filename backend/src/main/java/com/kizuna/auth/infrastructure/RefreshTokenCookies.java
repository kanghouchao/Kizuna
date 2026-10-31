package com.kizuna.auth.infrastructure;

import com.kizuna.shared.config.AppProperties;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * リフレッシュトークンの cookie。JS から読ませず（HttpOnly）、別サイトからの要求に付けさせず（SameSite=Strict）、送り先を {@code /api/auth}
 * に絞る。{@code Domain} を付けずホスト限定にする。{@code Secure} はアプリの scheme が https のときだけ付ける（http の開発・e2e 環境でも
 * cookie を受け取れるようにするため）。
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookies {

  public static final String NAME = "refresh_token";

  private static final String PATH = "/api/auth";

  private final AppProperties appProperties;

  public ResponseCookie issue(IssuedJwt refresh) {
    long remainingSeconds =
        Math.max(0, Duration.between(Instant.now(), refresh.expiresAt()).toSeconds());
    return builder(refresh.value()).maxAge(remainingSeconds).build();
  }

  public ResponseCookie clear() {
    return builder("").maxAge(0).build();
  }

  private ResponseCookie.ResponseCookieBuilder builder(String value) {
    return ResponseCookie.from(NAME, value)
        .httpOnly(true)
        .secure(AppProperties.Scheme.HTTPS.getName().equals(appProperties.getScheme()))
        .sameSite("Strict")
        .path(PATH);
  }
}
