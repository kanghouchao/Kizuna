package com.kizuna.user.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** パスワードの長さ制約（最小 8 文字・最大 72 バイト）を検証する。二択の要求のため未指定は制約に触れない。 */
class StoreManagerAppointRequestTest {

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

  private StoreManagerAppointRequest request(String password) {
    StoreManagerAppointRequest req = new StoreManagerAppointRequest();
    req.setPassword(password);
    return req;
  }

  @Test
  void password72Bytes_passes() {
    assertThat(validator.validateProperty(request("あ".repeat(24)), "password")).isEmpty();
  }

  @Test
  void password73Bytes_violates() {
    assertThat(validator.validateProperty(request("あ".repeat(24) + "a"), "password")).isNotEmpty();
  }

  @Test
  void password7Chars_violates() {
    assertThat(validator.validateProperty(request("abcdefg"), "password")).isNotEmpty();
  }

  @Test
  void password8Chars_passes() {
    assertThat(validator.validateProperty(request("abcdefgh"), "password")).isEmpty();
  }

  @Test
  void nullPassword_passes() {
    // user_id を送る既存アカウント任命の形。必須判定は application 層が担う。
    assertThat(validator.validateProperty(request(null), "password")).isEmpty();
  }
}
