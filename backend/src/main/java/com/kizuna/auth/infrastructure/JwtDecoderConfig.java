package com.kizuna.auth.infrastructure;

import com.kizuna.shared.config.AppProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Bearer トークン検証用の {@link JwtDecoder} を組み立てる。secret は {@link JwtIssuer}
 * の発行側と同一のバイト表現から導出する。署名アルゴリズムは発行側と両側で HS256 に固定する。
 */
@Configuration
public class JwtDecoderConfig {

  @Bean
  public JwtDecoder jwtDecoder(
      AppProperties appProperties,
      TokenBlacklistValidator tokenBlacklistValidator,
      CredentialVersionValidator credentialVersionValidator) {
    return decoder(
        appProperties,
        List.of(
            // 既定の 60 秒スキューを明示的に 0 へ。ブラックリスト TTL は exp までのため、
            // スキューがあるとその秒数ぶん「失効済みだが exp 未到来」の token が fail-open する窓ができる。
            new JwtTimestampValidator(Duration.ZERO),
            new TokenTypeValidator(TokenType.ACCESS),
            tokenBlacklistValidator,
            // 受理候補の版を DB で照合する検証は、連鎖の最後に置く。
            credentialVersionValidator));
  }

  /**
   * 署名（HS256）と issuer を検証し、続けて {@code validators} を先頭から順に最初の失敗まで評価する decoder。発行は {@link JwtIssuer}
   * の 1 箇所のみで iss は固定のため、全リクエストを単一 issuer で揃える。
   */
  static NimbusJwtDecoder decoder(
      AppProperties appProperties, List<OAuth2TokenValidator<Jwt>> validators) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(HmacSecretKeyFactory.create(appProperties))
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    List<OAuth2TokenValidator<Jwt>> chain = new ArrayList<>();
    chain.add(new JwtIssuerValidator(JwtIssuer.ISSUER));
    chain.addAll(validators);
    var delegating = new DelegatingOAuth2TokenValidator<>(chain);
    delegating.setFailOnError(true);
    decoder.setJwtValidator(delegating);
    return decoder;
  }
}
