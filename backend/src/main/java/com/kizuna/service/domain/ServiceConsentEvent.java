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
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "t_service_consent_events")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
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
