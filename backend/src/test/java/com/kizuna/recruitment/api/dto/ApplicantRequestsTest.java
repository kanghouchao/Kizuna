package com.kizuna.recruitment.api.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.cast.application.CastCreateCommand;
import com.kizuna.cast.domain.CastEnrollmentStatus;
import com.kizuna.recruitment.application.ApplicantCommands;
import com.kizuna.recruitment.domain.ApplicantSourceType;
import com.kizuna.recruitment.domain.ApplicantStatus;
import com.kizuna.recruitment.domain.ReceptionChannel;
import com.kizuna.shared.exception.ServiceException;
import jakarta.validation.Validation;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class ApplicantRequestsTest {
  @Test
  void blankOptionalEmailPassesWireValidationAndNormalizesBeforeStorage() {
    var mapper =
        JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    var request =
        mapper.readValue(
            "{\"name\":\"応募者\",\"channel\":\"WEB\",\"source_type\":\"DIRECT\",\"email\":\"   \"}",
            ApplicantIntakeRequest.class);
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      assertThat(factory.getValidator().validate(request)).isEmpty();
    }
    assertThat(request.toIntake().email()).isNull();
  }

  @Test
  void checklistKeysAreDataAndInvalidValuesNeverBecomeRenamedBindingPaths() {
    var values = new HashMap<String, Boolean>();
    values.put("identityChecked", null);
    var request = new ApplicantInterviewRequest(0L, OffsetDateTime.now(), "担当", null, values);
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      assertThat(factory.getValidator().validate(request)).isEmpty();
    }
    assertThatThrownBy(request::command)
        .isInstanceOf(ServiceException.class)
        .hasMessage("確認項目は項目名と確認状況の組で30件以内にしてください");
    var valid =
        new ApplicantInterviewRequest(
            0L, OffsetDateTime.now(), "担当", null, Map.of("identityChecked", true));
    assertThat(valid.command().interview().checklist()).containsOnlyKeys("identityChecked");
  }

  @Test
  void commandsCarryTheVersionAndEveryInputUnchanged() {
    var intake =
        new ApplicantIntakeRequest(
            "応募者",
            ReceptionChannel.WEB,
            ApplicantSourceType.DIRECT,
            null,
            null,
            "担当",
            "000",
            null,
            "住所",
            "経歴",
            "希望");
    assertThat(new ApplicantUpdateRequest(4L, intake).command())
        .isEqualTo(new ApplicantCommands.IntakeUpdate(4L, intake.toIntake()));
    assertThat(new ApplicantTransitionRequest(5L, ApplicantStatus.REJECTED, "  理由  ").command())
        .isEqualTo(new ApplicantCommands.StatusChange(5L, ApplicantStatus.REJECTED, "  理由  "));
    var enrollment =
        new ApplicantEnrollmentRequest(
            6L, "源氏名", "SUSPENDED", LocalDate.of(2026, 11, 1), "p", "紹介", 20, 160, 1, 2, 3, 7);
    assertThat(enrollment.command())
        .isEqualTo(
            new ApplicantCommands.Enrollment(
                6L,
                new CastCreateCommand(
                    "源氏名",
                    CastEnrollmentStatus.SUSPENDED,
                    LocalDate.of(2026, 11, 1),
                    "p",
                    "紹介",
                    20,
                    160,
                    1,
                    2,
                    3,
                    7)));
    assertThat(
            new ApplicantEnrollmentRequest(
                    0L,
                    "源氏名",
                    null,
                    LocalDate.of(2026, 11, 1),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null)
                .command()
                .cast()
                .status())
        .isNull();
  }
}
