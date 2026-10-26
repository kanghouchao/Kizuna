package com.kizuna.service.domain;

import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "t_service_consent_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ServiceConsentEvent extends StoreScopedEntity {
  @Column(nullable = false, updatable = false)
  private String consentId;

  @Column(nullable = false, updatable = false)
  private long revisionNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false)
  private ConsentDecision decision;

  @Column(nullable = false, updatable = false)
  private long termsVersion;

  @Column(nullable = false, updatable = false)
  private String serviceRevisionId;

  @Column(nullable = false, updatable = false)
  private Long actorId;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime occurredAt;

  public static ServiceConsentEvent record(ServiceConsent consent, Long actorId) {
    var event = new ServiceConsentEvent();
    event.setStoreId(consent.getStoreId());
    event.consentId = consent.getId();
    event.revisionNumber = consent.getRevisionNumber();
    event.decision = consent.getDecision();
    event.termsVersion = consent.getTermsVersion();
    event.serviceRevisionId = consent.getServiceRevisionId();
    event.actorId = actorId;
    event.occurredAt = OffsetDateTime.now();
    return event;
  }
}
