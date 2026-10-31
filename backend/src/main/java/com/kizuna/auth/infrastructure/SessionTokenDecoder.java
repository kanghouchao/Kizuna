package com.kizuna.auth.infrastructure;

import com.kizuna.auth.infrastructure.RefreshTokenRejectedException.Reason;
import com.kizuna.shared.config.AppProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * リフレッシュトークンの検証とログアウト対象の sid の読み取り。リフレッシュは Bearer と同じ検証の連鎖（ブラックリスト・資格情報の版）に セッションの失効を加え、種別の条件だけを
 * {@code typ=refresh} に替える。拒否の原因は検証の失敗コードで区別する。
 */
@Component
public class SessionTokenDecoder {

  private static final String EXPIRED = "expired";

  private final NimbusJwtDecoder refreshDecoder;
  private final NimbusJwtDecoder signatureOnlyDecoder;

  public SessionTokenDecoder(
      AppProperties appProperties,
      TokenBlacklistValidator tokenBlacklistValidator,
      SessionBlacklistValidator sessionBlacklistValidator,
      CredentialVersionValidator credentialVersionValidator) {
    JwtTimestampValidator timestamp = new JwtTimestampValidator(Duration.ZERO);
    this.refreshDecoder =
        JwtDecoderConfig.decoder(
            appProperties,
            List.of(
                // 種別を期限より先に見る — 期限切れのアクセストークンを expired と答えない。
                new TokenTypeValidator(TokenType.REFRESH),
                // 有効期間は発行時ではなく現在の設定で数え直す。セッションの失効キーの TTL は現在の設定値なので、
                // 運用中に有効期間を短縮しても、失効キーより長生きする既発行トークンを残さない。
                jwt ->
                    timestamp.validate(jwt).hasErrors()
                            || jwt.getIssuedAt() == null
                            || !jwt.getIssuedAt()
                                .plusMillis(appProperties.getJwt().getRefreshExpiration())
                                .isAfter(Instant.now())
                        ? OAuth2TokenValidatorResult.failure(new OAuth2Error(EXPIRED))
                        : OAuth2TokenValidatorResult.success(),
                tokenBlacklistValidator,
                sessionBlacklistValidator,
                credentialVersionValidator));
    // ログアウトは失効させるだけなので期限を問わない。署名と issuer は検証する（偽造の sid で他人のセッションを止めさせない）。
    this.signatureOnlyDecoder = JwtDecoderConfig.decoder(appProperties, List.of());
  }

  public Jwt decodeRefresh(String token) {
    if (token == null || token.isBlank()) {
      throw new RefreshTokenRejectedException(Reason.MISSING);
    }
    try {
      return refreshDecoder.decode(token);
    } catch (JwtValidationException ex) {
      String code = ex.getErrors().iterator().next().getErrorCode();
      throw new RefreshTokenRejectedException(
          switch (code) {
            case EXPIRED -> Reason.EXPIRED;
            case CredentialVersionValidator.CREDENTIAL_CHANGED -> Reason.CREDENTIAL_CHANGED;
            default -> Reason.INVALID;
          });
    } catch (JwtException ex) {
      throw new RefreshTokenRejectedException(Reason.INVALID);
    }
  }

  /** 署名の正しいトークンの sid。期限切れでも読む。 */
  public Optional<String> sessionIdOf(String token) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.ofNullable(
          signatureOnlyDecoder.decode(token).getClaimAsString(TokenType.SESSION_ID_CLAIM));
    } catch (JwtException ex) {
      return Optional.empty();
    }
  }
}
