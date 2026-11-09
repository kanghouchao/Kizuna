package com.kizuna.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.shared.exception.ServiceException;
import com.kizuna.survey.domain.SurveyInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class SurveyInputTest {

  @ParameterizedTest
  @NullSource
  @ValueSource(
      strings = {"123", "0190F5A8-0000-7000-8000-000000000001", "0190f5a8000070008000000000000001"})
  void idRejectsAnythingButALowercaseUuid(String id) {
    assertThatThrownBy(() -> SurveyInput.id(id)).isInstanceOf(ServiceException.class);
  }

  @Test
  void idAcceptsALowercaseUuid() {
    assertThat(SurveyInput.id("0190f5a8-0000-7000-8000-000000000001"))
        .isEqualTo("0190f5a8-0000-7000-8000-000000000001");
  }
}
