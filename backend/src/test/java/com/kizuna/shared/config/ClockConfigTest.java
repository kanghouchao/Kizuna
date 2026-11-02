package com.kizuna.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ClockConfigTest {

  @Test
  void readsTheBusinessZoneAtTheDatabasePrecision() {
    var clock = new ClockConfig().clock(new AppProperties());

    assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Tokyo"));
    for (var i = 0; i < 100; i++) {
      assertThat(clock.instant().getNano() % 1_000).isZero();
    }
  }
}
