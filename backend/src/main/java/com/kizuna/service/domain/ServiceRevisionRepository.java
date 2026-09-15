package com.kizuna.service.domain;

import com.kizuna.service.application.CourseTerms;
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

  String COURSE_SELECT =
      """
      select new com.kizuna.service.application.CourseTerms(
        r.serviceId, r.id, r.revisionNumber, r.afterTerms.name,
        r.afterTerms.durationMinutes, r.afterTerms.price, r.afterTerms.remuneration,
        r.occurredAt, s.deleted)
      from ServiceRevision r join ServiceItem s on s.id = r.serviceId
      where r.afterTerms.kind = com.kizuna.service.domain.ServiceKind.COURSE
        and r.operation <> com.kizuna.service.domain.ServiceRevision.Operation.DELETED
      """;

  @Query(COURSE_SELECT + " and r.serviceId = :serviceId and r.revisionNumber = :number")
  Optional<CourseTerms> findCourse(String serviceId, long number);

  @Query(COURSE_SELECT + " and r.id = :id")
  Optional<CourseTerms> findHistoricalCourse(String id);

  @Query(
      COURSE_SELECT
          + " and s.deleted = false and r.revisionNumber = s.revisionNumber"
          + " and locate(lower(:search), lower(r.afterTerms.name)) > 0"
          + " order by r.afterTerms.name, r.serviceId")
  Page<CourseTerms> findCurrentCourses(String search, Pageable pageable);

  @Query(
      COURSE_SELECT
          + " and locate(lower(:search), lower(r.afterTerms.name)) > 0"
          + " and (r.occurredAt < :at or (r.occurredAt = :at and r.id < :id))"
          + " order by r.occurredAt desc, r.id desc")
  List<CourseTerms> findHistoricalCourses(
      String search, OffsetDateTime at, String id, Pageable pageable);
}
