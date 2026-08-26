package com.kizuna.auth.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** 新パスワードの長さ制約（最小 8 文字・最大 72 バイト）を検証する。 */
class PasswordChangeRequestTest {

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

  private PasswordChangeRequest request(String newPassword) {
    PasswordChangeRequest req = new PasswordChangeRequest();
    req.setCurrentPassword("current-password");
    req.setNewPassword(newPassword);
    return req;
  }

  @Test
  void password72Bytes_passes() {
    assertThat(validator.validateProperty(request("あ".repeat(24)), "newPassword")).isEmpty();
  }

  @Test
  void password73Bytes_violates() {
    assertThat(validator.validateProperty(request("あ".repeat(24) + "a"), "newPassword"))
        .isNotEmpty();
  }

  @Test
  void password7Chars_violates() {
    assertThat(validator.validateProperty(request("abcdefg"), "newPassword")).isNotEmpty();
  }

  @Test
  void password8Chars_passes() {
    assertThat(validator.validateProperty(request("abcdefgh"), "newPassword")).isEmpty();
  }
}
