package com.kizuna.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** {@link TokenBlacklistValidator} の単体テスト。 */
class TokenBlacklistValidatorTest {

  private final TokenBlacklistService tokenBlacklistService = mock(TokenBlacklistService.class);
  private final TokenBlacklistValidator validator =
      new TokenBlacklistValidator(tokenBlacklistService);

  private Jwt jwt(String tokenValue, String subject) {
    return Jwt.withTokenValue(tokenValue).header("alg", "HS256").subject(subject).build();
  }

  @Test
  @DisplayName("トークン・ユーザーいずれもブラックリスト未登録なら成功")
  void succeedsWhenNeitherBlacklisted() {
    when(tokenBlacklistService.isBlacklisted("token-a")).thenReturn(false);
    when(tokenBlacklistService.isUserBlacklisted("user@example.com")).thenReturn(false);

    OAuth2TokenValidatorResult result = validator.validate(jwt("token-a", "user@example.com"));

    assertThat(result.hasErrors()).isFalse();
  }

  @Test
  @DisplayName("トークン単位ブラックリスト登録済みなら失敗")
  void failsWhenTokenBlacklisted() {
    when(tokenBlacklistService.isBlacklisted("token-b")).thenReturn(true);

    OAuth2TokenValidatorResult result = validator.validate(jwt("token-b", "user@example.com"));

    assertThat(result.hasErrors()).isTrue();
  }

  @Test
  @DisplayName("ユーザー単位ブラックリスト登録済みなら失敗")
  void failsWhenUserBlacklisted() {
    when(tokenBlacklistService.isBlacklisted("token-c")).thenReturn(false);
    when(tokenBlacklistService.isUserBlacklisted("stopped@example.com")).thenReturn(true);

    OAuth2TokenValidatorResult result = validator.validate(jwt("token-c", "stopped@example.com"));

    assertThat(result.hasErrors()).isTrue();
  }

  @Test
  @DisplayName("失敗時の OAuth2Error は内部理由を description に含めない")
  void failureErrorHasNoLeakingDescription() {
    when(tokenBlacklistService.isBlacklisted("token-d")).thenReturn(true);

    OAuth2TokenValidatorResult result = validator.validate(jwt("token-d", "user@example.com"));

    assertThat(result.getErrors()).allSatisfy(error -> assertThat(error.getDescription()).isNull());
  }
}
