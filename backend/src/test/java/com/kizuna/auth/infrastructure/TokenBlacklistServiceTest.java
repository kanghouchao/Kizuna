package com.kizuna.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.shared.config.AppProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

  @Mock private RedisTemplate<String, Object> redisTemplate;

  @Mock private AppProperties appProperties;

  @InjectMocks private TokenBlacklistService service;

  @Test
  @SuppressWarnings("unchecked")
  void blacklistUser_writesUserKeyWithJwtExpirationTtl() {
    ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(appProperties.getJwtExpiration()).thenReturn(3_600_000L);

    service.blacklistUser("stopped@kizuna.test");

    verify(valueOperations)
        .set(eq("blacklist:users:stopped@kizuna.test"), eq("1"), eq(Duration.ofMillis(3_600_000L)));
  }

  @Test
  @SuppressWarnings("unchecked")
  void blacklist_writesTokenKeyWithJwtExpirationTtl() {
    ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(appProperties.getJwtExpiration()).thenReturn(3_600_000L);

    service.blacklist("Bearer xxx");

    verify(valueOperations)
        .set(eq("blacklist:tokens:xxx"), eq("1"), eq(Duration.ofMillis(3_600_000L)));
  }

  @Test
  @SuppressWarnings("unchecked")
  void blacklist_rawToken_stripsNoBearerPrefixAndWritesAsIs() {
    ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(appProperties.getJwtExpiration()).thenReturn(3_600_000L);

    service.blacklist("raw-token-without-bearer-prefix");

    verify(valueOperations)
        .set(
            eq("blacklist:tokens:raw-token-without-bearer-prefix"),
            eq("1"),
            eq(Duration.ofMillis(3_600_000L)));
  }

  @Test
  void blacklist_nullInput_writesNothing() {
    service.blacklist(null);

    verify(redisTemplate, never()).opsForValue();
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
