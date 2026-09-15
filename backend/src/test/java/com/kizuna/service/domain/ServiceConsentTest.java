package com.kizuna.service.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.shared.exception.ConflictException;
import org.junit.jupiter.api.Test;

class ServiceConsentTest {
  @Test
  void consentKeepsOldTermsAndDistinguishesRejectionFromRevision() {
    var consent = ServiceConsent.create("enrollment", "service");
    assertThat(consent.status(1)).isEqualTo(ConsentStatus.NOT_ACCEPTED);
    assertThat(consent.decide(ConsentDecision.ACCEPTED, 1, "revision1", 0)).isTrue();
    assertThat(consent.status(1)).isEqualTo(ConsentStatus.ACCEPTED);
    assertThat(consent.status(2)).isEqualTo(ConsentStatus.RECONFIRMATION_REQUIRED);
    assertThat(consent.getTermsVersion()).isEqualTo(1);
    assertThatThrownBy(() -> consent.decide(ConsentDecision.REJECTED, 2, "revision2", 0))
        .isInstanceOf(ConflictException.class);
    assertThat(consent.decide(ConsentDecision.REJECTED, 2, "revision2", 1)).isTrue();
    assertThat(consent.status(3)).isEqualTo(ConsentStatus.REJECTED);
    assertThat(consent.decide(ConsentDecision.REJECTED, 2, "revision2", 2)).isFalse();
    assertThat(consent.getRevisionNumber()).isEqualTo(2);
    assertThat(consent.decide(ConsentDecision.ACCEPTED, 3, "revision3", 2)).isTrue();
    assertThat(consent.status(3)).isEqualTo(ConsentStatus.ACCEPTED);
  }
}
