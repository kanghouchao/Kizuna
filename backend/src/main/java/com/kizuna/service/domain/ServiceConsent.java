package com.kizuna.service.domain;

import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "t_service_consents")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ServiceConsent extends StoreScopedEntity {
  @Column(nullable = false, updatable = false)
  private String enrollmentId;

  @Column(nullable = false, updatable = false)
  private String serviceId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ConsentDecision decision;

  @Column(nullable = false)
  private long termsVersion;

  @Column(nullable = false)
  private String serviceRevisionId;

  @Column(nullable = false)
  private long revisionNumber;

  public static ServiceConsent create(String enrollmentId, String serviceId) {
    var result = new ServiceConsent();
    result.enrollmentId = enrollmentId;
    result.serviceId = serviceId;
    return result;
  }

  public ConsentStatus status(long currentTermsVersion) {
    if (decision == null) return ConsentStatus.NOT_ACCEPTED;
    if (decision == ConsentDecision.REJECTED) return ConsentStatus.REJECTED;
    return termsVersion == currentTermsVersion
        ? ConsentStatus.ACCEPTED
        : ConsentStatus.RECONFIRMATION_REQUIRED;
  }

  public boolean decide(ConsentDecision next, long terms, String revisionId, long expectedVersion) {
    if (expectedVersion != revisionNumber)
      throw new ConflictException("意思が変更されています。最新の内容を再取得して確認してください");
    if (decision == next && termsVersion == terms) return false;
    decision = Objects.requireNonNull(next);
    termsVersion = terms;
    serviceRevisionId = revisionId;
    revisionNumber++;
    return true;
  }
}
