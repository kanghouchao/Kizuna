package com.kizuna.cast.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.shared.exception.ServiceException;
import java.time.OffsetDateTime;
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
    assertThatThrownBy(enrollment::resume).isInstanceOf(ServiceException.class);
  }

  @Test
  void withdrawalRecordsItsTimeAndCannotBeRepeated() {
    CastEnrollment enrollment = CastEnrollment.builder().build();
    OffsetDateTime now = OffsetDateTime.parse("2026-09-10T12:00:00+09:00");
    enrollment.withdraw(now);
    assertThat(enrollment.getStatus()).isEqualTo(CastEnrollmentStatus.WITHDRAWN);
    assertThat(enrollment.getEndedAt()).isEqualTo(now);
    assertThatThrownBy(() -> enrollment.withdraw(now.plusHours(1)))
        .isInstanceOf(ServiceException.class);
  }

  @Test
  void suspensionAndResumptionRejectRepeatedOrTerminalOperations() {
    CastEnrollment enrollment = CastEnrollment.builder().build();
    assertThatThrownBy(enrollment::resume).isInstanceOf(ServiceException.class);
    enrollment.suspend();
    assertThat(enrollment.getStatus()).isEqualTo(CastEnrollmentStatus.SUSPENDED);
    assertThatThrownBy(enrollment::suspend).isInstanceOf(ServiceException.class);
    enrollment.resume();
    assertThat(enrollment.getStatus()).isEqualTo(CastEnrollmentStatus.ENROLLED);
    enrollment.suspend();
    enrollment.withdraw(OffsetDateTime.now());
    assertThatThrownBy(enrollment::suspend).isInstanceOf(ServiceException.class);
    assertThatThrownBy(enrollment::resume).isInstanceOf(ServiceException.class);
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
