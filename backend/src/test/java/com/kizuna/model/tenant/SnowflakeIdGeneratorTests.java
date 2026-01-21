package com.kizuna.model.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.model.entity.tenant.SnowflakeIdGenerator;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class SnowflakeIdGeneratorTests {

  @Test
  void rejectsInvalidWorkerId() {
    long tooLarge = (1L << 10) + 1; // WORKER_ID_BITS = 10
    assertThatThrownBy(() -> new SnowflakeIdGenerator(-1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SnowflakeIdGenerator(tooLarge))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void generatesMonotonicIncreasingIds() {
    SnowflakeIdGenerator gen = new SnowflakeIdGenerator(1);
    long prev = -1;
    for (int i = 0; i < 2000; i++) {
      String idStr = (String) gen.generate(null, null);
      long id = Long.parseLong(idStr);
      assertThat(id).isGreaterThan(prev);
      prev = id;
    }
  }

  @RepeatedTest(3)
  void generatesUniqueIdsOverManyCalls() {
    SnowflakeIdGenerator gen = new SnowflakeIdGenerator(2);
    Set<String> ids = new HashSet<>();
    for (int i = 0; i < 5000; i++) {
      String idStr = (String) gen.generate(null, null);
      assertThat(ids.add(idStr)).as("ID %s should be unique", idStr).isTrue();
    }
  }
}
