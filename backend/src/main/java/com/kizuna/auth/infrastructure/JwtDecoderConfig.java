package com.kizuna.auth.infrastructure;

import com.kizuna.shared.config.AppProperties;
import java.time.Duration;
import javax.crypto.SecretKey;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Bearer トークン検証用の {@link JwtDecoder} を組み立てる。secret は {@link PlatformJwtIssuer}
 * の発行側と同一のバイト表現から導出する。署名アルゴリズムは発行側と両側で HS256 に固定する。
 */
@Configuration
public class JwtDecoderConfig {

  @Bean
  public JwtDecoder jwtDecoder(
      AppProperties appProperties, TokenBlacklistValidator tokenBlacklistValidator) {
    SecretKey secretKey = HmacSecretKeyFactory.create(appProperties);
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            // 既定の 60 秒スキューを明示的に 0 へ。ブラックリスト TTL は exp までのため、
            // スキューがあるとその秒数ぶん「失効済みだが exp 未到来」の token が fail-open する窓ができる。
            new JwtTimestampValidator(Duration.ZERO),
            // 発行は PlatformAuth の 1 箇所のみ。/files 等ドメイン外パスも含め全リクエスト単一 issuer で揃える。
            new JwtIssuerValidator(PlatformJwtIssuer.ISSUER_PLATFORM),
            tokenBlacklistValidator));
    return decoder;
  }
}
