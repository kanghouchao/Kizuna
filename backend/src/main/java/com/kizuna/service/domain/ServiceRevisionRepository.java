package com.kizuna.service.domain;

import com.kizuna.service.application.OrderServiceTerms;
import com.kizuna.service.application.SpecialServiceTerms;
import com.kizuna.shared.persistence.StoreScopedRepository;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

public interface ServiceRevisionRepository extends StoreScopedRepository<ServiceRevision> {
  Optional<ServiceRevision> findByStoreIdAndServiceIdAndRevisionNumber(
      Long storeId, String serviceId, long revisionNumber);

  Page<ServiceRevision> findByStoreIdAndServiceId(
      Long storeId, String serviceId, Pageable pageable);

  String SPECIAL_FROM =
      """
      from ServiceRevision r join ServiceItem s on s.id = r.serviceId
      where r.storeId = :storeId and s.storeId = r.storeId
        and r.afterTerms.kind = com.kizuna.service.domain.ServiceKind.SPECIAL_SERVICE
        and r.operation <> com.kizuna.service.domain.ServiceRevision.Operation.DELETED
      """;

  String SPECIAL_SELECT = "select r " + SPECIAL_FROM;

  @Query(SPECIAL_SELECT + " and r.id = :id")
  Optional<ServiceRevision> findHistoricalSpecial(Long storeId, String id);

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
      where r.storeId = :storeId and s.storeId = r.storeId and c.storeId = r.storeId
        and e.storeId = r.storeId and enrollment.storeId = r.storeId
        and r.afterTerms.kind = com.kizuna.service.domain.ServiceKind.SPECIAL_SERVICE
        and r.operation <> com.kizuna.service.domain.ServiceRevision.Operation.DELETED
        and s.deleted = false and r.revisionNumber = s.revisionNumber
        and c.decision = com.kizuna.service.domain.ConsentDecision.ACCEPTED
        and c.termsVersion = s.termsVersion
        and enrollment.status = com.kizuna.cast.domain.CastEnrollmentStatus.ENROLLED
        and locate(lower(:search), lower(r.afterTerms.name)) > 0
      """)
  Page<SpecialServiceTerms> findAcceptedSpecials(
      Long storeId, String enrollment, String search, Pageable pageable);

  String NAME_MATCHES = " and locate(lower(:search), lower(r.afterTerms.name)) > 0";

  @Query(
      value = SPECIAL_SELECT + NAME_MATCHES,
      countQuery = "select count(r) " + SPECIAL_FROM + NAME_MATCHES)
  Page<ServiceRevision> findHistoricalSpecials(Long storeId, String search, Pageable pageable);

  String SELECTION_FROM =
      """
      from ServiceRevision r join ServiceItem s on s.id = r.serviceId
      where r.storeId = :storeId and s.storeId = r.storeId
        and r.afterTerms.kind = :kind
        and r.operation <> com.kizuna.service.domain.ServiceRevision.Operation.DELETED
      """;

  String SELECTION_SELECT =
      """
      select new com.kizuna.service.application.OrderServiceTerms(
        r.serviceId, r.id, r.revisionNumber, r.afterTerms.name,
        r.afterTerms.durationMinutes, r.afterTerms.price, r.afterTerms.remuneration,
        r.occurredAt, s.deleted)
      """
          + SELECTION_FROM;

  @Query(SELECTION_SELECT + " and r.serviceId = :serviceId and r.revisionNumber = :number")
  Optional<OrderServiceTerms> findSelection(
      Long storeId, ServiceKind kind, String serviceId, long number);

  @Query(SELECTION_SELECT + " and r.id = :id")
  Optional<OrderServiceTerms> findHistoricalSelection(Long storeId, ServiceKind kind, String id);

  @Query(
      SELECTION_SELECT
          + " and s.deleted = false and r.revisionNumber = s.revisionNumber"
          + " and locate(lower(:search), lower(r.afterTerms.name)) > 0")
  Page<OrderServiceTerms> findCurrentSelections(
      Long storeId, ServiceKind kind, String search, Pageable pageable);

  @Query(
      value = SELECTION_SELECT + NAME_MATCHES,
      countQuery = "select count(r) " + SELECTION_FROM + NAME_MATCHES)
  Page<OrderServiceTerms> findHistoricalSelections(
      Long storeId, ServiceKind kind, String search, Pageable pageable);
}
