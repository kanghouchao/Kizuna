package com.kizuna.cast.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.shared.exception.ServiceException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CastTest {
  @Test
  void enrollmentCannotRelink() {
    CastEnrollment enrollment = CastEnrollment.builder().build();
    enrollment.linkCast(1L);
    assertThat(enrollment.getCastId()).isEqualTo(1L);
    assertThatThrownBy(() -> enrollment.linkCast(2L))
        .isInstanceOf(CastInvitationStateException.class);
  }

  @Test
  void withdrawnIsTerminal() {
    CastEnrollment enrollment =
        CastEnrollment.builder().status(CastEnrollmentStatus.WITHDRAWN).build();
    assertThatThrownBy(() -> enrollment.changeStatus(CastEnrollmentStatus.ENROLLED))
        .isInstanceOf(ServiceException.class);
  }

  @Test
  void publicationIsIndependentOfEnrollment() {
    CastProfile profile = CastProfile.builder().name("花").build();
    assertThat(profile.getPublicationStatus()).isEqualTo(CastPublicationStatus.UNPUBLISHED);
    profile.changePublication(CastPublicationStatus.PUBLISHED);
    assertThat(profile.getPublicationStatus()).isEqualTo(CastPublicationStatus.PUBLISHED);
    profile.changePublication(CastPublicationStatus.UNPUBLISHED);
    assertThat(profile.getPublicationStatus()).isEqualTo(CastPublicationStatus.UNPUBLISHED);
  }

  @Test
  void internalValuesAreReplacedAndRemovable() {
    CastEnrollment enrollment = CastEnrollment.builder().build();
    enrollment.replaceCustomFields(Map.of("memo", "旧"));
    enrollment.replaceCustomFields(Map.of("contract", "新"));
    assertThat(enrollment.getCustomFields()).containsExactlyEntriesOf(Map.of("contract", "新"));
    enrollment.removeCustomField("contract");
    assertThat(enrollment.getCustomFields()).isEmpty();
  }
}
