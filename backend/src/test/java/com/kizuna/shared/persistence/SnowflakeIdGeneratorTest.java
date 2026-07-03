package com.kizuna.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SnowflakeIdGeneratorTest {

  @Test
  @DisplayName("生成される ID は 10 進数文字列で、単調増加かつ一意であること")
  void generate_producesUniqueMonotonicIds() {
    SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1L);
    Set<Long> seen = new HashSet<>();
    long previous = -1L;
    // 同一ミリ秒内のシーケンス増加とオーバーフロー時の待機分岐も通す
    for (int i = 0; i < 10_000; i++) {
      String id = (String) generator.generate(null, null);
      assertThat(id).matches("\\d+");
      long value = Long.parseLong(id);
      assertThat(value).isGreaterThan(previous);
      assertThat(seen.add(value)).isTrue();
      previous = value;
    }
  }

  @Test
  @DisplayName("デフォルトコンストラクタでも ID を生成できること")
  void generate_withDefaultWorkerId() {
    SnowflakeIdGenerator generator = new SnowflakeIdGenerator();
    assertThat((String) generator.generate(null, null)).matches("\\d+");
  }

  @Test
  @DisplayName("workerId が範囲外なら IllegalArgumentException を投げること")
  void constructor_rejectsOutOfRangeWorkerId() {
    assertThatThrownBy(() -> new SnowflakeIdGenerator(-1L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SnowflakeIdGenerator(1024L))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
