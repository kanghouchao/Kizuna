package com.kizuna.recruitment.reporting;

import com.kizuna.shared.exception.ServiceUnavailableException;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecruitmentHireReader {
  private final EntityManager em;

  /** 応募元が媒体でない入店は mediaName が null。 */
  public record Hire(String mediaName, LocalDate startedOn, OffsetDateTime endedAt) {}

  @PreAuthorize("hasAuthority('PERM_RECRUITMENT_VIEW')")
  @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
  public List<Hire> read(Long storeId, LocalDate from, LocalDate to, int limit) {
    if (storeId == null) throw new AccessDeniedException("店舗の指定が必要です");
    if (limit < 1 || limit > 100_000) throw tooLarge();
    var rows =
        em.createQuery(
                """
        select new com.kizuna.recruitment.reporting.RecruitmentHireReader$Hire(
          case when a.sourceType = com.kizuna.recruitment.domain.ApplicantSourceType.MEDIA
            then a.sourceMedia else null end,
          e.startedOn, e.endedAt)
        from com.kizuna.recruitment.domain.Applicant a
        join com.kizuna.cast.domain.CastEnrollment e
          on e.id = a.enrollmentId and e.storeId = a.storeId
        where a.storeId = :store and e.storeId = :store
          and a.status = com.kizuna.recruitment.domain.ApplicantStatus.HIRED
          and e.startedOn >= :from and e.startedOn <= :to
        order by e.id
        """,
                Hire.class)
            .setParameter("store", storeId)
            .setParameter("from", from)
            .setParameter("to", to)
            .setMaxResults(limit + 1)
            .getResultList();
    if (rows.size() > limit) throw tooLarge();
    return List.copyOf(rows);
  }

  private static ServiceUnavailableException tooLarge() {
    return new ServiceUnavailableException("入店記録が処理可能な上限を超えています。対象を確認してください");
  }
}
