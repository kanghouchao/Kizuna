package com.kizuna.order.remuneration;

import com.kizuna.order.infrastructure.RemunerationQuery;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
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

  public record Basis(
      LocalDate businessDate, String orderId, long accruedRemuneration, boolean invalidated) {}

  public List<Basis> basis(Long storeId, Long personId, LocalDate start, LocalDate end) {
    return query.basis(storeId, personId, start, end).stream()
        .map(
            o -> new Basis(o.businessDate(), o.orderId(), o.accruedRemuneration(), o.invalidated()))
        .toList();
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
