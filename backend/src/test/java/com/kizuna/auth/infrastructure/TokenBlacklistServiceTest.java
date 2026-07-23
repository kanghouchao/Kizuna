package com.kizuna.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.shared.config.AppProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.oauth2.jwt.JwtEncoder;

class TokenBlacklistServiceTest {

  private static final String SECRET =
      "tokenblacklistservicetestsecrettokenblacklistservicetestsecret";
  private static final long JWT_EXPIRATION_MILLIS = 3_600_000L;

  private AppProperties appProperties;
  private RedisTemplate<String, Object> redisTemplate;
  private TokenBlacklistService service;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    appProperties = new AppProperties();
    AppProperties.Jwt jwt = new AppProperties.Jwt();
    jwt.setSecret(SECRET);
    jwt.setExpiration(JWT_EXPIRATION_MILLIS);
    appProperties.setJwt(jwt);
    redisTemplate = mock(RedisTemplate.class);
    // 実コンストラクタが HmacSecretKeyFactory 経由で decoder を組み立てるため、mock ではなく実 AppProperties を渡す。
    service = new TokenBlacklistService(redisTemplate, appProperties);
  }

  /** service と同一 secret で実トークンを発行する（blacklist() の decoder が実際に解読できる必要があるため）。 */
  private String issueToken() {
    JwtEncoder encoder = new JwtEncoderConfig().jwtEncoder(appProperties);
    PlatformJwtIssuer issuer = new PlatformJwtIssuer(encoder, appProperties);
    return issuer.issue("user@kizuna.test", Map.of("authorities", List.of("PERM_TEST"))).token();
  }

  @Test
  @SuppressWarnings("unchecked")
  void blacklist_bearerToken_writesTokenKeyWithTtlUntilActualExpiry() {
    ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    String token = issueToken();

    service.blacklist("Bearer " + token);

    ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
    verify(valueOperations).set(eq("blacklist:tokens:" + token), eq("1"), ttlCaptor.capture());
    // token 自身の exp までの残存時間。発行から assert までの実行時間ぶんの誤差を許容する。
    assertThat(ttlCaptor.getValue().toMillis())
        .isCloseTo(JWT_EXPIRATION_MILLIS, Offset.offset(5_000L));
  }

  @Test
  @SuppressWarnings("unchecked")
  void blacklist_rawTokenWithoutBearerPrefix_writesAsIsWithActualExpiryTtl() {
    ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    String token = issueToken();

    service.blacklist(token);

    ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
    verify(valueOperations).set(eq("blacklist:tokens:" + token), eq("1"), ttlCaptor.capture());
    assertThat(ttlCaptor.getValue().toMillis())
        .isCloseTo(JWT_EXPIRATION_MILLIS, Offset.offset(5_000L));
  }

  @Test
  void blacklist_invalidToken_writesNothing() {
    service.blacklist("Bearer not-a-real-jwt");

    verify(redisTemplate, never()).opsForValue();
  }

  @Test
  void blacklist_nullInput_writesNothing() {
    service.blacklist(null);

    verify(redisTemplate, never()).opsForValue();
  }

  @Test
  @SuppressWarnings("unchecked")
  void blacklistUser_writesUserKeyWithJwtExpirationTtl() {
    ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    service.blacklistUser("stopped@kizuna.test");

    verify(valueOperations)
        .set(
            eq("blacklist:users:stopped@kizuna.test"),
            eq("1"),
            eq(Duration.ofMillis(JWT_EXPIRATION_MILLIS)));
  }

  @Test
  void clearUser_deletesUserKey() {
    service.clearUser("resumed@kizuna.test");

    verify(redisTemplate).delete("blacklist:users:resumed@kizuna.test");
  }

  @Test
  void isUserBlacklisted_true_whenKeyExists() {
    when(redisTemplate.hasKey("blacklist:users:blocked@kizuna.test")).thenReturn(true);

    assertThat(service.isUserBlacklisted("blocked@kizuna.test")).isTrue();
  }

  @Test
  void isUserBlacklisted_false_whenKeyAbsent() {
    when(redisTemplate.hasKey("blacklist:users:clean@kizuna.test")).thenReturn(false);

    assertThat(service.isUserBlacklisted("clean@kizuna.test")).isFalse();
  }
}
