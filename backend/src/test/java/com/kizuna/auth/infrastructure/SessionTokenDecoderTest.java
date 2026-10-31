package com.kizuna.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.auth.infrastructure.RefreshTokenRejectedException.Reason;
import com.kizuna.shared.config.AppProperties;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

class SessionTokenDecoderTest {

  private static final String SECRET =
      "sessiontokendecodertestsecretsessiontokendecodertestsecret12";

  private AppProperties appProperties;
  private JwtIssuer issuer;
  private TokenBlacklistService blacklist;
  private CredentialVersionService versions;
  private SessionTokenDecoder decoder;

  @BeforeEach
  void setUp() {
    appProperties = new AppProperties();
    AppProperties.Jwt jwt = new AppProperties.Jwt();
    jwt.setSecret(SECRET);
    jwt.setExpiration(3_600_000L);
    jwt.setAccessExpiration(900_000L);
    jwt.setRefreshExpiration(1_209_600_000L);
    appProperties.setJwt(jwt);
    issuer = new JwtIssuer(new JwtEncoderConfig().jwtEncoder(appProperties), appProperties);
    blacklist = mock(TokenBlacklistService.class);
    versions = mock(CredentialVersionService.class);
    lenient().when(versions.isCurrent(anyLong(), anyLong())).thenReturn(true);
    decoder =
        new SessionTokenDecoder(
            appProperties,
            new TokenBlacklistValidator(blacklist),
            new SessionBlacklistValidator(blacklist),
            new CredentialVersionValidator(versions));
  }

  private String token(TokenType type) {
    return issuer.issue(type, "u@kizuna.test", claims(), "session-1").value();
  }

  private static Map<String, Object> claims() {
    return Map.of(CredentialVersionService.CLAIM, 3L, CredentialVersionService.USER_ID_CLAIM, 7L);
  }

  private String expiredRefreshToken() {
    JwtEncoder encoder = new JwtEncoderConfig().jwtEncoder(appProperties);
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(JwtIssuer.ISSUER)
            .subject("u@kizuna.test")
            .issuedAt(now.minusSeconds(20))
            .expiresAt(now.minusSeconds(10))
            .claims(c -> c.putAll(claims()))
            .claim(TokenType.CLAIM, TokenType.REFRESH.claimValue())
            .claim(TokenType.SESSION_ID_CLAIM, "session-1")
            .build();
    return encoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }

  private Reason reasonOf(String token) {
    try {
      decoder.decodeRefresh(token);
    } catch (RefreshTokenRejectedException ex) {
      return ex.getReason();
    }
    throw new AssertionError("拒否されませんでした");
  }

  @Test
  @DisplayName("有効なリフレッシュトークンは利用者 ID と sid を読める")
  void decodesValidRefreshToken() {
    Jwt jwt = decoder.decodeRefresh(token(TokenType.REFRESH));

    assertThat(jwt.getClaimAsString(TokenType.SESSION_ID_CLAIM)).isEqualTo("session-1");
    assertThat(jwt.getClaims().get(CredentialVersionService.USER_ID_CLAIM)).isEqualTo(7L);
  }

  @Test
  @DisplayName("cookie が無ければ missing")
  void missingToken() {
    assertThat(reasonOf(null)).isEqualTo(Reason.MISSING);
    assertThat(reasonOf("")).isEqualTo(Reason.MISSING);
  }

  @Test
  @DisplayName("期限切れは expired")
  void expiredToken() {
    assertThat(reasonOf(expiredRefreshToken())).isEqualTo(Reason.EXPIRED);
  }

  @Test
  @DisplayName("版の不一致は credential_changed")
  void staleCredentialVersion() {
    when(versions.isCurrent(7L, 3L)).thenReturn(false);

    assertThat(reasonOf(token(TokenType.REFRESH))).isEqualTo(Reason.CREDENTIAL_CHANGED);
  }

  @Test
  @DisplayName("アクセストークン・署名不正・ログアウト済みのセッション・トークン単位の失効は invalid")
  void invalidTokens() {
    assertThat(reasonOf(token(TokenType.ACCESS))).isEqualTo(Reason.INVALID);
    assertThat(reasonOf("not-a-jwt")).isEqualTo(Reason.INVALID);

    when(blacklist.isSessionBlacklisted("session-1")).thenReturn(true);
    assertThat(reasonOf(token(TokenType.REFRESH))).isEqualTo(Reason.INVALID);

    when(blacklist.isSessionBlacklisted("session-1")).thenReturn(false);
    when(blacklist.isBlacklisted(anyString())).thenReturn(true);
    assertThat(reasonOf(token(TokenType.REFRESH))).isEqualTo(Reason.INVALID);
  }

  @Test
  @DisplayName("sid は署名を検証したうえで期限切れのトークンからも読める")
  void sessionIdIgnoresExpiry() {
    assertThat(decoder.sessionIdOf(expiredRefreshToken())).contains("session-1");
    assertThat(decoder.sessionIdOf(token(TokenType.ACCESS))).contains("session-1");
    assertThat(decoder.sessionIdOf("not-a-jwt")).isEmpty();
    assertThat(decoder.sessionIdOf(null)).isEmpty();
  }

  @Test
  @DisplayName("署名鍵の違うトークンからは sid を読まない")
  void sessionIdRequiresSignature() {
    AppProperties other = new AppProperties();
    AppProperties.Jwt jwt = new AppProperties.Jwt();
    jwt.setSecret("anothersecretanothersecretanothersecret1234");
    jwt.setAccessExpiration(900_000L);
    other.setJwt(jwt);
    String forged =
        new JwtIssuer(new JwtEncoderConfig().jwtEncoder(other), other)
            .issue(TokenType.ACCESS, "u", claims(), "session-x")
            .value();

    assertThat(decoder.sessionIdOf(forged)).isEmpty();
    assertThatThrownBy(() -> decoder.decodeRefresh(forged))
        .isInstanceOf(RefreshTokenRejectedException.class);
  }
}
