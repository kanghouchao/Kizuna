package com.kizuna.recruitment.domain;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ApplicantStatusHistoryRepository
    extends JpaRepository<ApplicantStatusHistory, String> {
  List<ApplicantStatusHistory> findByApplicantIdOrderByCreatedAtDescIdDesc(
      String applicantId, Limit limit);

  @Query(
      "select h from ApplicantStatusHistory h where h.applicantId = :applicantId and (h.createdAt < :at or (h.createdAt = :at and h.id < :id)) order by h.createdAt desc, h.id desc")
  List<ApplicantStatusHistory> findAfter(
      String applicantId, OffsetDateTime at, String id, Limit limit);
}
