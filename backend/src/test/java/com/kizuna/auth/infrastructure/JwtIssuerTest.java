package com.kizuna.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.shared.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * {@link JwtIssuer}（HS256 発行）と {@link NimbusJwtDecoder}（HS256 復号）の相互運用を固定する。 issuer が {@code
 * "Kizuna"}（非 URL）でも {@link JwtIssuerValidator} を通ることを含め、発行→復号の往復を検証する。
 */
class JwtIssuerTest {

  private static final String SECRET = "jwtissuertestsecretjwtissuertestsecret1234567890ab";

  private JwtIssuer issuer;
  private NimbusJwtDecoder decoder;

  @BeforeEach
  void setUp() {
    AppProperties appProperties = new AppProperties();
    AppProperties.Jwt jwt = new AppProperties.Jwt();
    jwt.setSecret(SECRET);
    jwt.setExpiration(3_600_000L);
    jwt.setAccessExpiration(900_000L);
    jwt.setRefreshExpiration(1_209_600_000L);
    appProperties.setJwt(jwt);
    issuer = new JwtIssuer(new JwtEncoderConfig().jwtEncoder(appProperties), appProperties);

    SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(new JwtIssuerValidator(JwtIssuer.ISSUER));
  }

  @Test
  void issuedTokenRoundTripsThroughHs256Decoder() {
    IssuedJwt issued =
        issuer.issue(
            TokenType.ACCESS,
            "user@kizuna.test",
            Map.of("authorities", List.of("PERM_TEST"), "userType", "STAFF"),
            "session-1");

    Jwt decoded = decoder.decode(issued.value());

    // iss = "Kizuna" は非 URL のため getIssuer()（内部で URL 変換する）ではなく
    // getClaimAsString で読む（JwtIssuerValidator 自身も内部で同様に生クレーム文字列を比較する）。
    assertThat(decoded.getSubject()).isEqualTo("user@kizuna.test");
    assertThat(decoded.getClaimAsString("iss")).isEqualTo(JwtIssuer.ISSUER);
    assertThat(decoded.getClaimAsStringList("authorities")).containsExactly("PERM_TEST");
    // exp クレームは JWT 仕様上 秒精度（NumericDate）のため、ミリ秒値を秒へ切り捨てて比較する。
    assertThat(decoded.getExpiresAt().getEpochSecond())
        .isEqualTo(issued.expiresAt().getEpochSecond());
  }

  @Test
  void stampsTypeAndSessionId() {
    Jwt access = decoder.decode(issuer.issue(TokenType.ACCESS, "u", Map.of(), "session-1").value());
    Jwt refresh =
        decoder.decode(issuer.issue(TokenType.REFRESH, "u", Map.of(), "session-1").value());

    assertThat(access.getClaimAsString(TokenType.CLAIM)).isEqualTo("access");
    assertThat(refresh.getClaimAsString(TokenType.CLAIM)).isEqualTo("refresh");
    assertThat(access.getClaimAsString(TokenType.SESSION_ID_CLAIM)).isEqualTo("session-1");
    assertThat(refresh.getClaimAsString(TokenType.SESSION_ID_CLAIM)).isEqualTo("session-1");
  }

  @Test
  void eachTypeUsesItsOwnLifetime() {
    Instant before = Instant.now();

    IssuedJwt access = issuer.issue(TokenType.ACCESS, "u", Map.of(), "s");
    IssuedJwt refresh = issuer.issue(TokenType.REFRESH, "u", Map.of(), "s");

    // 大域の app.jwt.expiration（1 時間）ではなく、種別ごとの有効期間で発行される。
    assertThat(access.expiresAt())
        .isBetween(before.plusMillis(900_000L), Instant.now().plusMillis(900_000L));
    assertThat(refresh.expiresAt())
        .isBetween(before.plusMillis(1_209_600_000L), Instant.now().plusMillis(1_209_600_000L));
  }
}
