package com.kizuna.cast.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** update リクエストの Bean Validation 制約（label は非空・長さ、null は「変更しない」ため許容）を検証する。 */
class CastFieldDefinitionUpdateRequestTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void setUp() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void tearDown() {
    factory.close();
  }

  private CastFieldDefinitionUpdateRequest request(String label) {
    CastFieldDefinitionUpdateRequest req = new CastFieldDefinitionUpdateRequest();
    req.setLabel(label);
    return req;
  }

  @Test
  void nonBlankLabel_passes() {
    assertThat(validator.validate(request("血液型"))).isEmpty();
  }

  @Test
  void nullLabel_passes() {
    // null は「変更しない」を意味する（PATCH セマンティクス）ため制約に掛からず更新を素通りさせる。
    assertThat(validator.validate(request(null))).isEmpty();
  }

  @Test
  void whitespaceOnlyLabel_violatesPattern() {
    assertThat(validator.validate(request(" "))).isNotEmpty();
  }

  @Test
  void emptyLabel_violatesPattern() {
    assertThat(validator.validate(request(""))).isNotEmpty();
  }

  @Test
  void labelExceeding100Chars_violatesSize() {
    assertThat(validator.validate(request("あ".repeat(101)))).isNotEmpty();
  }
}
