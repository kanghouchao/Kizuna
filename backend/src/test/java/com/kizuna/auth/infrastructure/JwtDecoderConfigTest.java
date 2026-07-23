package com.kizuna.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.shared.config.AppProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * {@link JwtDecoderConfig} の単体テスト。decoder への配線（multi-HS・skew=0・issuer・ブラックリスト）を jjwt
 * 発行トークンの実解読で固定する（地基テストと同じ手法をここでも用いる）。
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
    lenient()
        .when(tokenBlacklistService.isBlacklisted(org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(false);
    lenient()
        .when(tokenBlacklistService.isUserBlacklisted(org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(false);

    decoder =
        new JwtDecoderConfig()
            .jwtDecoder(appProperties, new TokenBlacklistValidator(tokenBlacklistService));
  }

  private String issueToken(String issuer, long expirationMillis) {
    AppProperties issuingProperties = new AppProperties();
    AppProperties.Jwt jwt = new AppProperties.Jwt();
    jwt.setSecret(SECRET);
    jwt.setExpiration(expirationMillis);
    issuingProperties.setJwt(jwt);
    JwtUtil jwtUtil = new JwtUtil(issuingProperties);
    return jwtUtil
        .generateToken("user@kizuna.test", issuer, Map.of("authorities", List.of("PERM_TEST")))
        .token();
  }

  @Test
  @DisplayName("正しい issuer・未失効・未ブラックリストの token は解読できること")
  void decodesValidToken() {
    String token = issueToken(JwtUtil.ISSUER_PLATFORM, 3_600_000L);

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
    String token = issueToken(JwtUtil.ISSUER_PLATFORM, -1_000L);

    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("トークン単位ブラックリスト登録済みの token は拒否されること")
  void rejectsBlacklistedToken() {
    when(tokenBlacklistService.isBlacklisted(org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(true);
    String token = issueToken(JwtUtil.ISSUER_PLATFORM, 3_600_000L);

    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("ユーザー単位ブラックリスト登録済みの token は拒否されること")
  void rejectsUserBlacklistedToken() {
    when(tokenBlacklistService.isUserBlacklisted("user@kizuna.test")).thenReturn(true);
    String token = issueToken(JwtUtil.ISSUER_PLATFORM, 3_600_000L);

    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
  }
}
