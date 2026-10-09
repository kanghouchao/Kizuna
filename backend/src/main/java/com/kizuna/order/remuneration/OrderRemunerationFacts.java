package com.kizuna.order.remuneration;

import com.kizuna.order.infrastructure.RemunerationQuery;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderRemunerationFacts {
  private final RemunerationQuery query;

  public record Item(
      String orderId,
      LocalDate businessDate,
      String serviceSummary,
      long accruedRemuneration,
      boolean completionInvalidated) {}

  public Map<LocalDate, Long> daily(Long storeId, Long personId, YearMonth month) {
    return query.dailyTotals(storeId, personId, month);
  }

  public Page<Item> orders(Long storeId, Long personId, YearMonth month, Pageable page) {
    return query
        .orders(storeId, personId, month, page)
        .map(
            o ->
                new Item(
                    o.orderId(),
                    o.businessDate(),
                    o.serviceSummary(),
                    o.accruedRemuneration(),
                    o.completionInvalidated()));
  }
}
