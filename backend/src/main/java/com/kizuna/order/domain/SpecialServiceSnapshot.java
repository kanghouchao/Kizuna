package com.kizuna.order.domain;

import java.io.Serializable;
import java.time.OffsetDateTime;

public record SpecialServiceSnapshot(
    String serviceId,
    String revisionId,
    long revisionNumber,
    long termsVersion,
    String name,
    String chargeType,
    int price,
    int remuneration,
    String adoptionBasis,
    OffsetDateTime adoptedAt,
    String enrollmentId,
    String consentEventId,
    Long consentVersion)
    implements Serializable {
  public SpecialServiceSnapshot {
    if (serviceId == null
        || revisionId == null
        || enrollmentId == null
        || name == null
        || name.isBlank()
        || price < 0
        || remuneration < 0
        || remuneration > price
        || !("FREE".equals(chargeType) || "PAID".equals(chargeType))
        || ("FREE".equals(chargeType) && (price != 0 || remuneration != 0))
        || !("ACCEPTED_TERMS".equals(adoptionBasis)
            || "HISTORICAL_CORRECTION".equals(adoptionBasis))
        || ("ACCEPTED_TERMS".equals(adoptionBasis)
            && (consentEventId == null || consentVersion == null)))
      throw new InvalidOrderFeeLineException("特殊サービスの採用条件が正しくありません");
  }
}
