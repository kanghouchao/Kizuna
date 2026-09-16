package com.kizuna.service.domain;

import com.kizuna.service.application.OrderServiceTerms;
import com.kizuna.service.application.SpecialServiceTerms;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ServiceRevisionRepository extends JpaRepository<ServiceRevision, String> {
  Optional<ServiceRevision> findByServiceIdAndRevisionNumber(String serviceId, long revisionNumber);

  List<ServiceRevision> findByServiceIdOrderByRevisionNumberDesc(String serviceId, Limit limit);

  List<ServiceRevision> findByServiceIdAndRevisionNumberLessThanOrderByRevisionNumberDesc(
      String serviceId, long revisionNumber, Limit limit);

  String SPECIAL_SELECT =
      """
      select r from ServiceRevision r join ServiceItem s on s.id = r.serviceId
      where r.afterTerms.kind = com.kizuna.service.domain.ServiceKind.SPECIAL_SERVICE
        and r.operation <> com.kizuna.service.domain.ServiceRevision.Operation.DELETED
      """;

  @Query(SPECIAL_SELECT + " and r.id = :id")
  Optional<ServiceRevision> findHistoricalSpecial(String id);

  @Query(
      """
      select new com.kizuna.service.application.SpecialServiceTerms(
        r.serviceId, r.id, r.revisionNumber, r.termsVersion, r.afterTerms.name,
        cast(r.afterTerms.chargeType as string), r.afterTerms.price, r.afterTerms.remuneration,
        e.id, c.revisionNumber, r.occurredAt, s.deleted)
      from ServiceRevision r join ServiceItem s on s.id = r.serviceId
        join ServiceConsent c on c.serviceId = s.id and c.enrollmentId = :enrollment
        join ServiceConsentEvent e on e.consentId = c.id and e.revisionNumber = c.revisionNumber
        join com.kizuna.cast.domain.CastEnrollment enrollment on enrollment.id = c.enrollmentId
      where r.afterTerms.kind = com.kizuna.service.domain.ServiceKind.SPECIAL_SERVICE
        and r.operation <> com.kizuna.service.domain.ServiceRevision.Operation.DELETED
        and s.deleted = false and r.revisionNumber = s.revisionNumber
        and c.decision = com.kizuna.service.domain.ConsentDecision.ACCEPTED
        and c.termsVersion = s.termsVersion
        and enrollment.status = com.kizuna.cast.domain.CastEnrollmentStatus.ENROLLED
        and locate(lower(:search), lower(r.afterTerms.name)) > 0
      order by r.afterTerms.name, r.serviceId
      """)
  Page<SpecialServiceTerms> findAcceptedSpecials(
      String enrollment, String search, Pageable pageable);

  @Query(
      SPECIAL_SELECT
          + " and locate(lower(:search), lower(r.afterTerms.name)) > 0"
          + " and (r.occurredAt < :at or (r.occurredAt = :at and r.id < :id))"
          + " order by r.occurredAt desc, r.id desc")
  List<ServiceRevision> findHistoricalSpecials(
      String search, OffsetDateTime at, String id, Pageable pageable);

  String SELECTION_SELECT =
      """
      select new com.kizuna.service.application.OrderServiceTerms(
        r.serviceId, r.id, r.revisionNumber, r.afterTerms.name,
        r.afterTerms.durationMinutes, r.afterTerms.price, r.afterTerms.remuneration,
        r.occurredAt, s.deleted)
      from ServiceRevision r join ServiceItem s on s.id = r.serviceId
      where r.afterTerms.kind = :kind
        and r.operation <> com.kizuna.service.domain.ServiceRevision.Operation.DELETED
      """;

  @Query(SELECTION_SELECT + " and r.serviceId = :serviceId and r.revisionNumber = :number")
  Optional<OrderServiceTerms> findSelection(ServiceKind kind, String serviceId, long number);

  @Query(SELECTION_SELECT + " and r.id = :id")
  Optional<OrderServiceTerms> findHistoricalSelection(ServiceKind kind, String id);

  @Query(
      SELECTION_SELECT
          + " and s.deleted = false and r.revisionNumber = s.revisionNumber"
          + " and locate(lower(:search), lower(r.afterTerms.name)) > 0"
          + " order by r.afterTerms.name, r.serviceId")
  Page<OrderServiceTerms> findCurrentSelections(ServiceKind kind, String search, Pageable pageable);

  @Query(
      SELECTION_SELECT
          + " and locate(lower(:search), lower(r.afterTerms.name)) > 0"
          + " and (r.occurredAt < :at or (r.occurredAt = :at and r.id < :id))"
          + " order by r.occurredAt desc, r.id desc")
  List<OrderServiceTerms> findHistoricalSelections(
      ServiceKind kind, String search, OffsetDateTime at, String id, Pageable pageable);
}
