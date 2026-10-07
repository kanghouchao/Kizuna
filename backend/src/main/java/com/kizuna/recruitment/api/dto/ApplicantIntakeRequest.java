package com.kizuna.recruitment.api.dto;

import com.kizuna.recruitment.domain.ApplicantIntake;
import com.kizuna.recruitment.domain.ApplicantSourceType;
import com.kizuna.recruitment.domain.ReceptionChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ApplicantIntakeRequest(
    @NotBlank(message = "必須項目を入力してください") @Size(max = 100, message = "100文字または件以内で指定してください")
        String name,
    @NotNull(message = "必須項目を指定してください") ReceptionChannel channel,
    @NotNull(message = "必須項目を指定してください") ApplicantSourceType sourceType,
    @Size(max = 200, message = "200文字または件以内で指定してください") String sourceMedia,
    @Size(max = 100, message = "100文字または件以内で指定してください") String referrer,
    @Size(max = 100, message = "100文字または件以内で指定してください") String assignee,
    @Size(max = 50, message = "50文字または件以内で指定してください") String phone,
    @Size(max = 254, message = "254文字または件以内で指定してください") String email,
    @Size(max = 500, message = "500文字または件以内で指定してください") String address,
    @Size(max = 3000, message = "3000文字または件以内で指定してください") String experience,
    @Size(max = 3000, message = "3000文字または件以内で指定してください") String desiredConditions) {
  public ApplicantIntake toIntake() {
    return new ApplicantIntake(
        name,
        channel,
        sourceType,
        sourceMedia,
        referrer,
        assignee,
        phone,
        email,
        address,
        experience,
        desiredConditions);
  }
}
