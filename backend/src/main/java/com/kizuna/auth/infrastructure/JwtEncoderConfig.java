package com.kizuna.auth.infrastructure;

import com.kizuna.shared.config.AppProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Bearer トークン発行用の {@link JwtEncoder} を組み立てる。鍵は {@link JwtDecoderConfig} の decoder と同一の HMAC 対称鍵。
 */
@Configuration
public class JwtEncoderConfig {

  @Bean
  public JwtEncoder jwtEncoder(AppProperties appProperties) {
    SecretKey key =
        new SecretKeySpec(
            appProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    JWKSource<SecurityContext> jwks = new ImmutableSecret<>(key);
    return new NimbusJwtEncoder(jwks);
  }
}
