package com.kizuna.order.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/** ゲスト予約申請の流量制限の単体テスト。 */
@ExtendWith(MockitoExtension.class)
class GuestApplicationRateLimiterTest {

  @Mock private RedisTemplate<String, Object> redisTemplate;

  @InjectMocks private GuestApplicationRateLimiter limiter;

  @SuppressWarnings("unchecked")
  @Captor
  private ArgumentCaptor<List<String>> keysCaptor;

  private void stubCount(Long count) {
    when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(count);
  }

  @Test
  @DisplayName("窓の上限までは通し、超えた分を撥ねること")
  void allowsUpToTheWindowLimitAndRefusesBeyondIt() {
    stubCount(5L);
    assertThat(limiter.tryConsume(1L, "203.0.113.7")).as("上限ちょうどは通ること").isTrue();

    stubCount(6L);
    assertThat(limiter.tryConsume(1L, "203.0.113.7")).as("上限を超えた分は撥ねること").isFalse();
  }

  @Test
  @DisplayName("計数と期限付けを 1 つの原子操作で行うこと（期限の無い鍵が残るとその発信元は永久に撥ねられる）")
  void countsAndExpiresInOneAtomicOperation() {
    stubCount(1L);

    limiter.tryConsume(1L, "203.0.113.7");

    // 増分と期限付けを別の往復に分けない。分けると、その間の失効で期限なしの鍵が生まれる
    verify(redisTemplate).execute(any(RedisScript.class), anyList(), any());
  }

  @Test
  @DisplayName("数えられなければ通さないこと（唯一の兜底なので計数の故障で素通りさせない）")
  void refusesWhenTheCountCannotBeObtained() {
    stubCount(null);

    assertThat(limiter.tryConsume(1L, "203.0.113.7")).isFalse();
  }

  @Test
  @DisplayName("数える鍵が店舗と発信元の組であること（ある店舗への集中が他店舗の受付を塞がない）")
  void countsPerStoreAndOrigin() {
    stubCount(1L);

    limiter.tryConsume(7L, "203.0.113.7");

    verify(redisTemplate).execute(any(RedisScript.class), keysCaptor.capture(), any());
    assertThat(keysCaptor.getValue()).hasSize(1);
    assertThat(keysCaptor.getValue().get(0)).contains("7").contains("203.0.113.7");
  }
}
