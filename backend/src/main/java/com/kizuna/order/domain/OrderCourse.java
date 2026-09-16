package com.kizuna.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Embeddable
public record OrderCourse(
    @Column(name = "course_service_id", nullable = false) String serviceId,
    @Column(name = "course_revision_id", nullable = false) String revisionId,
    @Column(name = "course_revision_number", nullable = false) long revisionNumber,
    @Column(name = "course_name", nullable = false) String name,
    @Column(name = "course_minutes", nullable = false) int durationMinutes,
    @Column(name = "course_price", nullable = false) int price,
    @Column(name = "course_remuneration", nullable = false) int remuneration,
    @Column(name = "course_adoption_basis", nullable = false) String adoptionBasis,
    @Column(name = "course_adopted_at", nullable = false) OffsetDateTime adoptedAt)
    implements Serializable {
  public OrderCourse {
    if (serviceId == null
        || revisionId == null
        || name == null
        || name.isBlank()
        || durationMinutes <= 0
        || price < 0
        || remuneration < 0
        || remuneration > price
        || !("CURRENT_SETTING".equals(adoptionBasis)
            || "HISTORICAL_CORRECTION".equals(adoptionBasis))) {
      throw new InvalidOrderFeeLineException("コースの条件が正しくありません");
    }
  }
}
