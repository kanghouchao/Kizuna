package com.kizuna.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@JsonTest
class StrictScalarJsonModuleTest {

  enum Kind {
    SALES
  }

  record Scalars(
      String text,
      Boolean flag,
      Integer count,
      Long version,
      Kind kind,
      BigDecimal amount,
      OffsetDateTime at) {}

  record Required(@JsonProperty(required = true) String text) {}

  @Autowired JsonMapper mapper;

  static Stream<Arguments> rejected() {
    return Stream.of(
        Arguments.of("text", "1"),
        Arguments.of("text", "1.5"),
        Arguments.of("text", "true"),
        Arguments.of("text", "[]"),
        Arguments.of("text", "{}"),
        Arguments.of("flag", "\"true\""),
        Arguments.of("flag", "\"\""),
        Arguments.of("flag", "1"),
        Arguments.of("flag", "0"),
        Arguments.of("flag", "1.5"),
        Arguments.of("count", "\"1\""),
        Arguments.of("count", "\"\""),
        Arguments.of("count", "true"),
        Arguments.of("count", "1.5"),
        Arguments.of("count", "1.0"),
        Arguments.of("count", "2147483648"),
        Arguments.of("version", "\"1\""),
        Arguments.of("version", "\"\""),
        Arguments.of("version", "true"),
        Arguments.of("version", "1.5"),
        Arguments.of("version", "9223372036854775808"),
        Arguments.of("kind", "0"),
        Arguments.of("kind", "\"0\""),
        Arguments.of("kind", "\"\""),
        Arguments.of("kind", "\"sales\""),
        Arguments.of("kind", "true"),
        Arguments.of("at", "\"2026-01-01T00:00:00\""),
        Arguments.of("at", "true"));
  }

  @ParameterizedTest(name = "{0} は {1} を受けない")
  @MethodSource("rejected")
  void rejectsScalarOfAnotherJsonType(String field, String value) {
    assertThatThrownBy(() -> read(field, value)).isInstanceOf(JacksonException.class);
  }

  static Stream<Arguments> accepted() {
    return Stream.of(
        Arguments.of("text", "\"x\""),
        Arguments.of("text", "\"\""),
        Arguments.of("text", "null"),
        Arguments.of("flag", "false"),
        Arguments.of("count", "0"),
        Arguments.of("count", "-1"),
        Arguments.of("version", "2147483648"),
        Arguments.of("kind", "\"SALES\""),
        Arguments.of("amount", "100"),
        Arguments.of("amount", "1.5"),
        Arguments.of("at", "\"2026-01-01T00:00:00+09:00\""),
        Arguments.of("at", "1767225600"));
  }

  @ParameterizedTest(name = "{0} は {1} を受ける")
  @MethodSource("accepted")
  void acceptsDeclaredJsonType(String field, String value) {
    assertThat(read(field, value)).isNotNull();
  }

  @Test
  void requiredDistinguishesAbsentFromExplicitNull() {
    assertThat(mapper.readValue("{\"text\":null}", Required.class).text()).isNull();
    assertThatThrownBy(() -> mapper.readValue("{}", Required.class))
        .isInstanceOf(JacksonException.class);
  }

  private Scalars read(String field, String value) {
    return mapper.readValue("{\"" + field + "\":" + value + "}", Scalars.class);
  }
}
