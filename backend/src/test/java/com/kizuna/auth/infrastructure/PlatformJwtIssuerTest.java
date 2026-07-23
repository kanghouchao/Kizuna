package com.kizuna.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.auth.api.dto.Token;
import com.kizuna.shared.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * {@link PlatformJwtIssuer}（HS256 発行）と {@link NimbusJwtDecoder}（HS256 復号）の相互運用を固定する。 issuer が
 * {@code "PlatformAuth"}（非 URL）でも {@link JwtIssuerValidator} を通ることを含め、発行→復号の往復を検証する。
 */
class PlatformJwtIssuerTest {

  private static final String SECRET =
      "platformjwtissuertestsecretplatformjwtissuertestsecret12345678";

  @Test
  void issuedTokenRoundTripsThroughHs256Decoder() {
    AppProperties appProperties = new AppProperties();
    AppProperties.Jwt jwt = new AppProperties.Jwt();
    jwt.setSecret(SECRET);
    jwt.setExpiration(3_600_000L);
    appProperties.setJwt(jwt);

    JwtEncoder encoder = new JwtEncoderConfig().jwtEncoder(appProperties);
    PlatformJwtIssuer issuer = new PlatformJwtIssuer(encoder, appProperties);

    Token token =
        issuer.issue(
            "user@kizuna.test", Map.of("authorities", List.of("PERM_TEST"), "userType", "STAFF"));

    SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(new JwtIssuerValidator(PlatformJwtIssuer.ISSUER_PLATFORM));

    Jwt decoded = decoder.decode(token.token());

    // iss = "PlatformAuth" は非 URL のため getIssuer()（内部で URL 変換する）ではなく
    // getClaimAsString で読む（JwtIssuerValidator 自身も内部で同様に生クレーム文字列を比較する）。
    assertThat(decoded.getSubject()).isEqualTo("user@kizuna.test");
    assertThat(decoded.getClaimAsString("iss")).isEqualTo(PlatformJwtIssuer.ISSUER_PLATFORM);
    assertThat(decoded.getClaimAsStringList("authorities")).containsExactly("PERM_TEST");
    // exp クレームは JWT 仕様上 秒精度（NumericDate）のため、Token DTO のミリ秒値を秒へ切り捨てて比較する。
    assertThat(decoded.getExpiresAt().getEpochSecond()).isEqualTo(token.expiresAt() / 1000);
  }
}
