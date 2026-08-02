package com.kizuna.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/** {@link LineRegistrationTicketStore} の単体テスト。 */
class LineRegistrationTicketStoreTest {

  private static final String KEY_PREFIX = "line:registration:";

  private RedisTemplate<String, Object> redisTemplate;
  private ValueOperations<String, Object> valueOperations;
  private LineRegistrationTicketStore store;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    redisTemplate = mock(RedisTemplate.class);
    valueOperations = mock(ValueOperations.class);
    store = new LineRegistrationTicketStore(redisTemplate);
  }

  @Test
  @DisplayName("発行したチケットは推測不能で、10 分の TTL 付きで LINE ユーザー ID を保持する")
  void issueWritesLineUserIdWithTtl() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    String ticket = store.issue("U-line-1");

    ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
    verify(valueOperations).set(key.capture(), eq("U-line-1"), eq(Duration.ofMinutes(10)));
    assertThat(key.getValue()).isEqualTo(KEY_PREFIX + ticket);
    // 32 バイト乱数の base64url（パディングなし）＝43 文字。総当たり不能な長さであること。
    assertThat(ticket).hasSize(43).matches("[A-Za-z0-9_-]+");
  }

  @Test
  @DisplayName("発行のたびに異なるチケットになる")
  void issueGeneratesDistinctTickets() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    assertThat(store.issue("U-line-1")).isNotEqualTo(store.issue("U-line-1"));
  }

  @Test
  @DisplayName("消費は取得と削除を単一操作で行う（並行する二重登録でも一方しか通らない）")
  void consumeReadsAndDeletesAtomically() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.getAndDelete(KEY_PREFIX + "ticket-1")).thenReturn("U-line-1");

    assertThat(store.consume("ticket-1")).contains("U-line-1");
  }

  @Test
  @DisplayName("未知・期限切れ・使用済みのチケットは空を返す")
  void consumeReturnsEmptyForUnknownTicket() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.getAndDelete(KEY_PREFIX + "gone")).thenReturn(null);

    assertThat(store.consume("gone")).isEmpty();
  }

  @Test
  @DisplayName("空のチケットは Redis へ問い合わせずに空を返す")
  void consumeSkipsRedisForBlankTicket() {
    assertThat(store.consume("  ")).isEmpty();
    assertThat(store.consume(null)).isEmpty();

    verifyNoInteractions(redisTemplate);
  }
}
