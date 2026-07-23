package com.kizuna.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.shared.config.AppProperties;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * {@link JwtDecoderConfig} の単体テスト。decoder への配線（HS256 固定・skew=0・issuer・ブラックリスト）を実発行
 * トークンの実解読で固定する。issuer/exp を可変にしたテスト専用トークンは {@link JwtEncoderConfig} の encoder で直接組み立てる （{@link
 * PlatformJwtIssuer} は issuer を {@code ISSUER_PLATFORM} の 1 値へ固定しているため、issuer 不一致ケースを 表現できない）。
 */
class JwtDecoderConfigTest {

  private static final String SECRET =
      "jwtdecoderconfigtestsecretjwtdecoderconfigtestsecret1234567890";

  private AppProperties appProperties;
  private TokenBlacklistService tokenBlacklistService;
  private JwtDecoder decoder;

  @BeforeEach
  void setUp() {
    appProperties = new AppProperties();
    AppProperties.Jwt jwt = new AppProperties.Jwt();
    jwt.setSecret(SECRET);
    jwt.setExpiration(3_600_000L);
    appProperties.setJwt(jwt);

    tokenBlacklistService = mock(TokenBlacklistService.class);
    lenient().when(tokenBlacklistService.isBlacklisted(anyString())).thenReturn(false);
    lenient().when(tokenBlacklistService.isUserBlacklisted(anyString())).thenReturn(false);

    decoder =
        new JwtDecoderConfig()
            .jwtDecoder(appProperties, new TokenBlacklistValidator(tokenBlacklistService));
  }

  private String issueToken(String issuer, long expirationMillis) {
    JwtEncoder encoder = new JwtEncoderConfig().jwtEncoder(appProperties);
    Instant now = Instant.now();
    // 期限切れケース（expirationMillis 負値）でも exp が iat より後になるよう、iat を固定で過去へ置く
    // （Jwt は構築時に expiresAt.isAfter(issuedAt) を要求するため）。
    Instant issuedAt = now.minusSeconds(10);
    JwtClaimsSet claimsSet =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .subject("user@kizuna.test")
            .issuedAt(issuedAt)
            .expiresAt(now.plusMillis(expirationMillis))
            .claim("authorities", List.of("PERM_TEST"))
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return encoder.encode(JwtEncoderParameters.from(header, claimsSet)).getTokenValue();
  }

  @Test
  @DisplayName("正しい issuer・未失効・未ブラックリストの token は解読できること")
  void decodesValidToken() {
    String token = issueToken(PlatformJwtIssuer.ISSUER_PLATFORM, 3_600_000L);

    Jwt jwt = decoder.decode(token);

    assertThat(jwt.getSubject()).isEqualTo("user@kizuna.test");
  }

  @Test
  @DisplayName("issuer 不一致の token は拒否されること")
  void rejectsWrongIssuer() {
    String token = issueToken("OtherIssuer", 3_600_000L);

    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("期限切れの token は skew=0 で即座に拒否されること")
  void rejectsExpiredTokenWithZeroSkew() {
    String token = issueToken(PlatformJwtIssuer.ISSUER_PLATFORM, -1_000L);

    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("トークン単位ブラックリスト登録済みの token は拒否されること")
  void rejectsBlacklistedToken() {
    when(tokenBlacklistService.isBlacklisted(anyString())).thenReturn(true);
    String token = issueToken(PlatformJwtIssuer.ISSUER_PLATFORM, 3_600_000L);

    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("ユーザー単位ブラックリスト登録済みの token は拒否されること")
  void rejectsUserBlacklistedToken() {
    when(tokenBlacklistService.isUserBlacklisted("user@kizuna.test")).thenReturn(true);
    String token = issueToken(PlatformJwtIssuer.ISSUER_PLATFORM, 3_600_000L);

    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
  }
}
