package com.kizuna.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.json.JsonMapper;

@JsonTest
class JsonTimeZoneTest {

  @Autowired JsonMapper mapper;

  @Test
  void writesInstantsInUtcWhateverOffsetTheyCarry() {
    var tokyo = OffsetDateTime.parse("2026-11-02T10:00:00.123456+09:00");

    assertThat(mapper.writeValueAsString(tokyo)).isEqualTo("\"2026-11-02T01:00:00.123456Z\"");
  }

  @Test
  void readsInstantsInUtcWhateverOffsetTheyCarry() {
    var read = mapper.readValue("\"2026-11-02T10:00:00+09:00\"", OffsetDateTime.class);

    assertThat(read).isEqualTo(OffsetDateTime.parse("2026-11-02T01:00:00Z"));
    assertThat(read.getOffset()).isEqualTo(ZoneOffset.UTC);
  }
}
