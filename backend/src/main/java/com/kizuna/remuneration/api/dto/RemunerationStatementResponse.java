package com.kizuna.remuneration.api.dto;

import com.kizuna.order.remuneration.OrderRemunerationFacts;
import com.kizuna.remuneration.application.RemunerationStatement;
import com.kizuna.remuneration.application.RemunerationStatement.BonusItem;
import com.kizuna.remuneration.application.RemunerationStatement.Day;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Page;

public record RemunerationStatementResponse(
    Long storeId,
    String storeName,
    Long personId,
    String name,
    String month,
    OffsetDateTime generatedAt,
    long orderTotal,
    long knownGuaranteeTotal,
    Long guaranteeTotal,
    long bonusTotal,
    Long total,
    List<Day> days,
    Page<OrderRemunerationFacts.Item> orders,
    Page<BonusItem> bonusAwards) {
  public static RemunerationStatementResponse of(RemunerationStatement s) {
    return new RemunerationStatementResponse(
        s.storeId(),
        s.storeName(),
        s.personId(),
        s.name(),
        s.month(),
        s.generatedAt(),
        s.orderTotal(),
        s.knownGuaranteeTotal(),
        s.guaranteeTotal(),
        s.bonusTotal(),
        s.total(),
        s.days(),
        s.orders(),
        s.bonusAwards());
  }
}
