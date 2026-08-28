package com.kizuna.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** {@link CredentialVersionValidator} の単体テスト。 */
class CredentialVersionValidatorTest {

  private final CredentialVersionService credentialVersionService =
      mock(CredentialVersionService.class);
  private final CredentialVersionValidator validator =
      new CredentialVersionValidator(credentialVersionService);

  private Jwt jwt(Object credentialVersionClaim) {
    Jwt.Builder builder =
        Jwt.withTokenValue("token").header("alg", "HS256").subject("user@example.com");
    if (credentialVersionClaim != null) {
      builder.claim(CredentialVersionService.CLAIM, credentialVersionClaim);
    }
    return builder.build();
  }

  @Test
  @DisplayName("claim の版が現在と一致すれば成功")
  void succeedsWhenVersionIsCurrent() {
    when(credentialVersionService.isCurrent("user@example.com", 2L)).thenReturn(true);

    OAuth2TokenValidatorResult result = validator.validate(jwt(2L));

    assertThat(result.hasErrors()).isFalse();
  }

  @Test
  @DisplayName("claim の版が現在と不一致なら失敗")
  void failsWhenVersionIsStale() {
    when(credentialVersionService.isCurrent("user@example.com", 1L)).thenReturn(false);

    OAuth2TokenValidatorResult result = validator.validate(jwt(1L));

    assertThat(result.hasErrors()).isTrue();
  }

  @Test
  @DisplayName("claim 欠落は照合せずに失敗（移行期なしの fail-closed）")
  void failsWhenClaimIsMissing() {
    OAuth2TokenValidatorResult result = validator.validate(jwt(null));

    assertThat(result.hasErrors()).isTrue();
    verifyNoInteractions(credentialVersionService);
  }

  @Test
  @DisplayName("claim が数値でなければ照合せずに失敗")
  void failsWhenClaimIsNotANumber() {
    OAuth2TokenValidatorResult result = validator.validate(jwt("not-a-number"));

    assertThat(result.hasErrors()).isTrue();
    verifyNoInteractions(credentialVersionService);
  }

  @Test
  @DisplayName("失敗時の OAuth2Error は内部理由を description に含めない")
  void failureErrorHasNoLeakingDescription() {
    OAuth2TokenValidatorResult result = validator.validate(jwt(null));

    assertThat(result.getErrors()).allSatisfy(error -> assertThat(error.getDescription()).isNull());
  }
}
