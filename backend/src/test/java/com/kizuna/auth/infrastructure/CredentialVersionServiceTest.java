package com.kizuna.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.user.domain.PlatformUserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * {@link CredentialVersionService} の read-through の配線を固定する単体テスト。 単調書込みの収束（Lua の挙動）は模擬 Redis
 * では検証できないため、本物の Redis で走る統合テスト側で固定する。
 */
@ExtendWith(MockitoExtension.class)
class CredentialVersionServiceTest {

  private static final String EMAIL = "user@kizuna.test";
  private static final String KEY = "credential-version:" + EMAIL;
  private static final long JWT_EXPIRATION_MILLIS = 3_600_000L;

  @Mock private RedisTemplate<String, Object> redisTemplate;

  @Mock private PlatformUserRepository userRepository;

  private CredentialVersionService service;

  private ValueOperations<String, Object> valueOperations;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    AppProperties appProperties = new AppProperties();
    AppProperties.Jwt jwt = new AppProperties.Jwt();
    jwt.setSecret("credentialversionservicetestsecret1234567890abcdef");
    jwt.setExpiration(JWT_EXPIRATION_MILLIS);
    appProperties.setJwt(jwt);
    valueOperations = mock(ValueOperations.class);
    // lenient: reflect 系のテストは GET を経由しないため、この stub は使われない。
    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    service = new CredentialVersionService(redisTemplate, appProperties, userRepository);
  }

  private void cached(Object value) {
    when(valueOperations.get(KEY)).thenReturn(value);
  }

  @Test
  @DisplayName("キャッシュ一致なら DB を見ずに通す（定常経路は Redis のみ）")
  void cacheHitWithMatchingVersionAcceptsWithoutDb() {
    cached("3");

    assertThat(service.isCurrent(EMAIL, 3L)).isTrue();

    verifyNoInteractions(userRepository);
  }

  @Test
  @DisplayName("claim がキャッシュより旧いなら DB を見ずに拒否する（キャッシュ ≤ DB が常に成り立つため旧さが確定）")
  void cacheHitWithNewerCachedVersionRejectsWithoutDb() {
    cached("3");

    assertThat(service.isCurrent(EMAIL, 2L)).isFalse();

    verifyNoInteractions(userRepository);
  }

  @Test
  @SuppressWarnings("unchecked")
  @DisplayName("キャッシュ miss は DB のスカラー投影で照合し、現在値を単調書込みで埋め戻す")
  void cacheMissFallsBackToDbAndBackfills() {
    cached(null);
    when(userRepository.findCredentialVersionByEmail(EMAIL)).thenReturn(Optional.of(3L));

    assertThat(service.isCurrent(EMAIL, 3L)).isTrue();

    verify(redisTemplate)
        .execute(
            any(RedisScript.class),
            eq(List.of(KEY)),
            eq("3"),
            eq(String.valueOf(JWT_EXPIRATION_MILLIS)));
  }

  @Test
  @SuppressWarnings("unchecked")
  @DisplayName("claim がキャッシュより新しければ正本（DB）へ照合し直す — 反映漏れ直後の正当な新トークンを誤拒否しない")
  void claimNewerThanCacheReChecksDb() {
    cached("2");
    when(userRepository.findCredentialVersionByEmail(EMAIL)).thenReturn(Optional.of(3L));

    assertThat(service.isCurrent(EMAIL, 3L)).isTrue();

    verify(redisTemplate)
        .execute(
            any(RedisScript.class),
            eq(List.of(KEY)),
            eq("3"),
            eq(String.valueOf(JWT_EXPIRATION_MILLIS)));
  }

  @Test
  @DisplayName("DB 照合でも不一致なら拒否する（捏造された未来の版は正本で落ちる）")
  void claimAheadOfDbIsRejected() {
    cached(null);
    when(userRepository.findCredentialVersionByEmail(EMAIL)).thenReturn(Optional.of(3L));

    assertThat(service.isCurrent(EMAIL, 5L)).isFalse();
  }

  @Test
  @SuppressWarnings("unchecked")
  @DisplayName("主体不在は拒否し、埋め戻しも書かない（fail-closed）")
  void missingUserIsRejectedWithoutBackfill() {
    cached(null);
    when(userRepository.findCredentialVersionByEmail(EMAIL)).thenReturn(Optional.empty());

    assertThat(service.isCurrent(EMAIL, 0L)).isFalse();

    verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any(), any());
  }

  @Test
  @SuppressWarnings("unchecked")
  @DisplayName("reflect は単調書込みスクリプトを TTL = JWT 有効期間で実行する")
  void reflectWritesMonotonicallyWithJwtExpirationTtl() {
    service.reflect(EMAIL, 7L);

    verify(redisTemplate)
        .execute(
            any(RedisScript.class),
            eq(List.of(KEY)),
            eq("7"),
            eq(String.valueOf(JWT_EXPIRATION_MILLIS)));
  }

  @Test
  @DisplayName("Redis 断連の例外は貫通する（fail-closed — DB への隠れたフォールバックを作らない）")
  void redisFailurePropagates() {
    when(valueOperations.get(KEY)).thenThrow(new IllegalStateException("redis down"));

    assertThatThrownBy(() -> service.isCurrent(EMAIL, 1L))
        .isInstanceOf(IllegalStateException.class);

    verifyNoInteractions(userRepository);
  }
}
