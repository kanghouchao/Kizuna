package com.kizuna.shift.remuneration;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AttendanceFacts {
  private final EntityManager em;

  public record Interval(LocalDate businessDate, LocalDateTime start, LocalDateTime end) {}

  public List<Interval> between(Long storeId, Long personId, LocalDate start, LocalDate end) {
    return em.createQuery(
            """
     select new com.kizuna.shift.remuneration.AttendanceFacts$Interval(a.businessDate, a.actualStartAt, a.actualEndAt)
     from com.kizuna.shift.domain.Attendance a
     join com.kizuna.cast.domain.CastEnrollment e on e.id = a.castId and e.storeId = a.storeId
     where a.storeId = :store and e.castId = :person and a.cancelledAt is null
       and a.businessDate >= :start and a.businessDate <= :end
     order by a.businessDate, a.actualStartAt, a.id
     """,
            Interval.class)
        .setParameter("store", storeId)
        .setParameter("person", personId)
        .setParameter("start", start)
        .setParameter("end", end)
        .getResultList();
  }

  public record ReportInterval(
      Long storeId,
      Long personId,
      LocalDate businessDate,
      LocalDateTime start,
      LocalDateTime end) {}

  public List<ReportInterval> forReport(
      List<Long> stores, LocalDate from, LocalDate to, int limit) {
    return em.createQuery(
            """
      select new com.kizuna.shift.remuneration.AttendanceFacts$ReportInterval(
        a.storeId, e.castId, a.businessDate, a.actualStartAt, a.actualEndAt)
      from com.kizuna.shift.domain.Attendance a
      join com.kizuna.cast.domain.CastEnrollment e on e.id = a.castId and e.storeId = a.storeId
      where a.storeId in :stores and e.castId is not null and a.cancelledAt is null
        and a.businessDate >= :from and a.businessDate <= :to
      order by a.storeId, e.castId, a.businessDate, a.actualStartAt, a.id
      """,
            ReportInterval.class)
        .setParameter("stores", stores)
        .setParameter("from", from)
        .setParameter("to", to)
        .setMaxResults(limit + 1)
        .getResultList();
  }
}
