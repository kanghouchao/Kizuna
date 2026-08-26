package com.kizuna.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 平文パスワードの長さ制約。最小 8 文字、最大 72 <b>バイト</b>（UTF-8）。
 *
 * <p>上限が文字数でなくバイト数なのは BCrypt の受け入れ上限がバイト長だから — 72 バイト超を渡すと {@code BCrypt.hashpw} が
 * IllegalArgumentException を投げ、制約違反に写像されないまま 500 に落ちる。日本語なら 25 文字で超える。
 *
 * <p>null は許す。必須判定は各要求の {@code @NotBlank} または application 層が担う。
 */
@Documented
@Constraint(validatedBy = PasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Password {

  String message() default "password must be at least 8 characters and at most 72 bytes";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
