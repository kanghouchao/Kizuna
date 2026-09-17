package com.kizuna.order.application;

import com.kizuna.order.api.dto.SelfRemunerationItem;
import com.kizuna.order.domain.OrderCorrectionSnapshot;
import com.kizuna.order.domain.OrderFeeLineKind;
import java.util.ArrayList;
import java.util.List;

final class SelfRemunerationItems {
  private SelfRemunerationItems() {}

  static List<SelfRemunerationItem> from(OrderCorrectionSnapshot snapshot) {
    var result = new ArrayList<SelfRemunerationItem>();
    var c = snapshot.course();
    result.add(
        new SelfRemunerationItem(
            "COURSE",
            c.name(),
            c.price(),
            c.remuneration(),
            null,
            c.durationMinutes(),
            c.serviceId(),
            c.revisionId(),
            c.revisionNumber(),
            c.adoptionBasis(),
            c.adoptedAt(),
            null,
            null,
            null,
            null));
    for (var special : snapshot.specialServices()) {
      result.add(
          new SelfRemunerationItem(
              "SPECIAL_SERVICE",
              special.name(),
              special.price(),
              special.remuneration(),
              null,
              null,
              special.serviceId(),
              special.revisionId(),
              special.revisionNumber(),
              special.adoptionBasis(),
              special.adoptedAt(),
              special.chargeType(),
              special.termsVersion(),
              special.consentEventId(),
              special.consentVersion()));
    }
    for (var line : snapshot.feeLines()) {
      if (line.kind() != OrderFeeLineKind.EXTENSION && line.kind() != OrderFeeLineKind.SURCHARGE)
        continue;
      var a = line.adoption();
      result.add(
          new SelfRemunerationItem(
              line.kind().name(),
              line.name(),
              line.amount(),
              line.remuneration(),
              line.lineId(),
              line.durationMinutes(),
              line.serviceId(),
              a == null ? null : a.revisionId(),
              a == null ? null : a.revisionNumber(),
              a == null ? null : a.adoptionBasis(),
              a == null ? null : a.adoptedAt(),
              null,
              null,
              null,
              null));
    }
    return List.copyOf(result);
  }
}
