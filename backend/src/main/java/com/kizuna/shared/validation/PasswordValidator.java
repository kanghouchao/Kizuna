package com.kizuna.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

/** {@link Password} の検証。境界は BCrypt に合わせ 72 バイトちょうどは通し、73 バイトから撥ねる。 */
public class PasswordValidator implements ConstraintValidator<Password, String> {

  private static final int MIN_LENGTH = 8;
  private static final int MAX_BYTES = 72;

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }
    return value.length() >= MIN_LENGTH
        && value.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
  }
}
