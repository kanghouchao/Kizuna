package com.kizuna.order.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/** ゲスト予約申請の流量制限の単体テスト。 */
@ExtendWith(MockitoExtension.class)
class GuestApplicationRateLimiterTest {

  @Mock private RedisTemplate<String, Object> redisTemplate;
  @Mock private ValueOperations<String, Object> valueOperations;

  @InjectMocks private GuestApplicationRateLimiter limiter;

  @Captor private ArgumentCaptor<String> keyCaptor;

  @BeforeEach
  void setUp() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
  }

  @Test
  @DisplayName("窓の上限までは通し、超えた分を撥ねること")
  void allowsUpToTheWindowLimitAndRefusesBeyondIt() {
    when(valueOperations.increment(anyString())).thenReturn(5L, 6L);

    assertThat(limiter.tryConsume(1L, "203.0.113.7")).as("上限ちょうどは通ること").isTrue();
    assertThat(limiter.tryConsume(1L, "203.0.113.7")).as("上限を超えた分は撥ねること").isFalse();
  }

  @Test
  @DisplayName("鍵の生成と期限を 1 手で行うこと（期限の無い鍵が残るとその発信元は二度と申請できない）")
  void opensTheWindowAtomicallyWithItsExpiry() {
    when(valueOperations.increment(anyString())).thenReturn(1L);

    limiter.tryConsume(1L, "203.0.113.7");

    verify(valueOperations).setIfAbsent(anyString(), any(), any(Duration.class));
    // 期限を別手で張らない — 2 手の間で取りこぼすと期限なしの鍵が残る
    verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
  }

  @Test
  @DisplayName("2 件目以降で期限を張り直さないこと（張り直すと窓が滑り、連投で永久に切れない）")
  void doesNotRefreshTheExpiryOnSubsequentRequests() {
    when(valueOperations.increment(anyString())).thenReturn(2L);

    limiter.tryConsume(1L, "203.0.113.7");

    verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
  }

  @Test
  @DisplayName("数える鍵が店舗と発信元の組であること（ある店舗への集中が他店舗の受付を塞がない）")
  void countsPerStoreAndOrigin() {
    when(valueOperations.increment(anyString())).thenReturn(1L);

    limiter.tryConsume(7L, "203.0.113.7");

    verify(valueOperations).increment(keyCaptor.capture());
    assertThat(keyCaptor.getValue()).contains("7").contains("203.0.113.7");
  }
}
