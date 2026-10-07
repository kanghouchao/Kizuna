package com.kizuna.recruitment.api.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.shared.exception.ServiceException;
import jakarta.validation.Validation;
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
    assertThatThrownBy(request::toInterview)
        .isInstanceOf(ServiceException.class)
        .hasMessage("確認項目は項目名と確認状況の組で30件以内にしてください");
    var valid =
        new ApplicantInterviewRequest(
            0L, OffsetDateTime.now(), "担当", null, Map.of("identityChecked", true));
    assertThat(valid.toInterview().checklist()).containsOnlyKeys("identityChecked");
  }
}
