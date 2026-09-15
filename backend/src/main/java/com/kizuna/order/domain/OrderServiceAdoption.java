package com.kizuna.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.OffsetDateTime;

/** 設定から採用した条件の出所。名称・金額は明細自身が保持する。 */
@Embeddable
public record OrderServiceAdoption(
    @Column(name = "service_id") String serviceId,
    @Column(name = "revision_id") String revisionId,
    @Column(name = "revision_number") long revisionNumber,
    @Column(name = "adoption_basis") String adoptionBasis,
    @Column(name = "adopted_at") OffsetDateTime adoptedAt)
    implements Serializable {
  public OrderServiceAdoption {
    if (serviceId == null
        || revisionId == null
        || revisionNumber < 1
        || adoptedAt == null
        || !("CURRENT_SETTING".equals(adoptionBasis)
            || "ACCEPTED_TERMS".equals(adoptionBasis)
            || "HISTORICAL_CORRECTION".equals(adoptionBasis)))
      throw new InvalidOrderFeeLineException("採用条件の出所が正しくありません");
  }

  public static OrderServiceAdoption of(OrderCourse course) {
    return new OrderServiceAdoption(
        course.serviceId(),
        course.revisionId(),
        course.revisionNumber(),
        course.adoptionBasis(),
        course.adoptedAt());
  }
}
