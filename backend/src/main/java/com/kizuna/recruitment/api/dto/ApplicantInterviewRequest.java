package com.kizuna.recruitment.api.dto;

import com.kizuna.recruitment.domain.ApplicantInterview;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.Map;

public record ApplicantInterviewRequest(
    @NotNull(message = "必須項目を指定してください") @PositiveOrZero(message = "版は0以上で指定してください") Long version,
    @NotNull(message = "必須項目を指定してください") OffsetDateTime interviewAt,
    @NotBlank(message = "必須項目を入力してください") @Size(max = 100, message = "100文字または件以内で指定してください")
        String interviewer,
    @Size(max = 5000, message = "5000文字または件以内で指定してください") String notes,
    @NotNull(message = "必須項目を指定してください") @Size(max = 30, message = "30文字または件以内で指定してください")
        Map<String, Boolean> checklist) {
  public ApplicantInterview toInterview() {
    return new ApplicantInterview(interviewAt, interviewer, notes, checklist);
  }
}
