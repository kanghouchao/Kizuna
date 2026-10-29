package com.kizuna.recruitment.api.dto;

import com.kizuna.cast.application.CastCreateCommand;
import com.kizuna.cast.domain.CastEnrollmentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;

/** 在籍とプロフィールの入力は操作者が与え、応募者の個人情報・面接記録からは写さない。 */
public record ApplicantEnrollmentRequest(
    @NotNull(message = "必須項目を指定してください") @PositiveOrZero(message = "版は0以上で指定してください") Long version,
    @NotBlank(message = "源氏名を入力してください") String name,
    @Pattern(regexp = "ENROLLED|SUSPENDED", message = "在籍状態は ENROLLED または SUSPENDED です")
        String status,
    @NotNull(message = "入店日を入力してください") LocalDate startedOn,
    String photoUrl,
    String introduction,
    Integer age,
    Integer height,
    Integer bust,
    Integer waist,
    Integer hip,
    Integer displayOrder) {

  public CastCreateCommand toCommand() {
    return new CastCreateCommand(
        name,
        status == null ? null : CastEnrollmentStatus.valueOf(status),
        startedOn,
        photoUrl,
        introduction,
        age,
        height,
        bust,
        waist,
        hip,
        displayOrder);
  }
}
