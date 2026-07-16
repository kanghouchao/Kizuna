package com.kizuna.cast.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** create リクエストの Bean Validation 制約（key 正規表現・長さ、label 必須・長さ）を検証する。 */
class CastFieldDefinitionCreateRequestTest {

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

  private CastFieldDefinitionCreateRequest request(String key, String label) {
    CastFieldDefinitionCreateRequest req = new CastFieldDefinitionCreateRequest();
    req.setKey(key);
    req.setLabel(label);
    return req;
  }

  @Test
  void validKeyAndLabel_passes() {
    assertThat(validator.validate(request("blood_type", "血液型"))).isEmpty();
  }

  @Test
  void keyWithUppercase_violatesPattern() {
    assertThat(validator.validate(request("BloodType", "血液型"))).isNotEmpty();
  }

  @Test
  void keyStartingWithDigit_violatesPattern() {
    assertThat(validator.validate(request("1blood", "血液型"))).isNotEmpty();
  }

  @Test
  void reservedKeyConstructor_violatesPattern() {
    // 'constructor' は react-hook-form の register 内部予約名で、
    // 定義として作成されるとキャスト編集フォームの描画をクラッシュさせる（#277）。
    assertThat(validator.validate(request("constructor", "血液型"))).isNotEmpty();
  }

  @Test
  void reservedKeyPrototype_violatesPattern() {
    assertThat(validator.validate(request("prototype", "血液型"))).isNotEmpty();
  }

  @Test
  void keyContainingReservedWordButNotEqual_passes() {
    // 予約語そのものだけを禁止し、部分一致する別キーは従来どおり許可する。
    assertThat(validator.validate(request("constructors", "血液型"))).isEmpty();
    assertThat(validator.validate(request("my_constructor", "血液型"))).isEmpty();
  }

  @Test
  void keyExceeding50Chars_violatesSize() {
    assertThat(validator.validate(request("a".repeat(51), "血液型"))).isNotEmpty();
  }

  @Test
  void blankLabel_violatesNotBlank() {
    assertThat(validator.validate(request("blood_type", " "))).isNotEmpty();
  }

  @Test
  void labelExceeding100Chars_violatesSize() {
    assertThat(validator.validate(request("blood_type", "あ".repeat(101)))).isNotEmpty();
  }
}
