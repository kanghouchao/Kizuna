package com.kizuna.shared.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** 長さ判定そのものと、上限 72 バイトが BCrypt の受け入れ上限と一致することを固定する。 */
class PasswordValidatorTest {

  /** 日本語 24 文字 = UTF-8 で 72 バイトちょうど。 */
  private static final String LIMIT = "あ".repeat(24);

  private final PasswordValidator validator = new PasswordValidator();

  @Test
  void nullIsTolerated() {
    assertThat(validator.isValid(null, null)).isTrue();
  }

  @Test
  void exactly72Bytes_isValid() {
    assertThat(validator.isValid(LIMIT, null)).isTrue();
  }

  @Test
  void over72Bytes_isInvalid() {
    assertThat(validator.isValid(LIMIT + "a", null)).isFalse();
  }

  @Test
  void under8Chars_isInvalid() {
    assertThat(validator.isValid("abcdefg", null)).isFalse();
  }

  @Test
  void exactly8Chars_isValid() {
    assertThat(validator.isValid("abcdefgh", null)).isTrue();
  }

  @Test
  void asciiBoundaryIsCountedInBytes() {
    // 上限は文字数ではなくバイト数。ASCII は 1 文字 1 バイトなので 72 文字までが通る。
    assertThat(validator.isValid("a".repeat(72), null)).isTrue();
    assertThat(validator.isValid("a".repeat(73), null)).isFalse();
  }

  @Test
  void boundaryMatchesBCrypt() {
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    assertThatCode(() -> encoder.encode(LIMIT)).doesNotThrowAnyException();
    assertThatThrownBy(() -> encoder.encode(LIMIT + "a"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
