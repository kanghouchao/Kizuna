package com.kizuna.recruitment.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;

public interface ApplicantStatusHistoryRepository
    extends StoreScopedRepository<ApplicantStatusHistory> {
  List<ApplicantStatusHistory> findByStoreIdAndApplicantIdOrderByCreatedAtDescIdDesc(
      Long storeId, String applicantId, Limit limit);

  @Query(
      "select h from ApplicantStatusHistory h where h.storeId = :storeId and h.applicantId = :applicantId and (h.createdAt < :at or (h.createdAt = :at and h.id < :id)) order by h.createdAt desc, h.id desc")
  List<ApplicantStatusHistory> findAfter(
      Long storeId, String applicantId, OffsetDateTime at, String id, Limit limit);
}
