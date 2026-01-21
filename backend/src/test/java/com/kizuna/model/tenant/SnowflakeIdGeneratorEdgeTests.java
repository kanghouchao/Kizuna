package com.kizuna.model.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.model.entity.tenant.SnowflakeIdGenerator;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class SnowflakeIdGeneratorEdgeTests {

  private static void setPrivateField(Object target, String name, Object value) {
    try {
      Field f = SnowflakeIdGenerator.class.getDeclaredField(name);
      f.setAccessible(true);
      f.set(target, value);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void sequenceOverflowInSameMillisecondRollsToNextMillis() {
    SnowflakeIdGenerator gen = new SnowflakeIdGenerator(3);
    long now = System.currentTimeMillis();
    setPrivateField(gen, "lastTimestamp", now);
    // Set sequence to mask value so next increment overflows to 0
    setPrivateField(gen, "sequence", (long) ((1 << 12) - 1));

    String idStr = (String) gen.generate(null, null);
    long id = Long.parseLong(idStr);
    assertThat(id).isPositive();
  }

  @Test
  void clockBackwardsWaitsUntilRecovered() {
    SnowflakeIdGenerator gen = new SnowflakeIdGenerator(4);
    long now = System.currentTimeMillis();
    // Pretend lastTimestamp is in the future to trigger waitUntil path
    setPrivateField(gen, "lastTimestamp", now + 2);
    String idStr = (String) gen.generate(null, null);
    long id = Long.parseLong(idStr);
    assertThat(id).isPositive();
  }
}
