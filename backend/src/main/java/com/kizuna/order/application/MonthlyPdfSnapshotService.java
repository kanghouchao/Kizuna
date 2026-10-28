package com.kizuna.order.application;

import com.kizuna.cast.domain.CastRepository;
import com.kizuna.order.domain.RemunerationOrderRow;
import com.kizuna.order.infrastructure.RemunerationQuery;
import com.kizuna.order.infrastructure.StoreNames;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.user.application.ActorIdentityService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MonthlyPdfSnapshotService {
  private final MonthlyRemunerationService store;
  private final SelfMonthlyRemunerationService self;
  private final RemunerationQuery query;
  private final StoreNames stores;
  private final ActorIdentityService actors;
  private final CastRepository people;
  private final Clock clock;

  @Transactional(
      readOnly = true,
      isolation = Isolation.REPEATABLE_READ,
      timeoutString = "#{@appProperties.monthlyPdf.readTimeoutSeconds}")
  public MonthlyPdfSnapshot store(
      Long storeId, Long personId, String month, MonthlyPdfBudget budget) {
    var first = store.monthly(storeId, personId, month, 0, 1);
    return collect(
        storeId,
        personId,
        stores.name(storeId),
        first.name(),
        month,
        first.totalRemuneration(),
        first.orders().getTotalElements(),
        budget);
  }

  @Transactional(
      readOnly = true,
      isolation = Isolation.REPEATABLE_READ,
      timeoutString = "#{@appProperties.monthlyPdf.readTimeoutSeconds}")
  public MonthlyPdfSnapshot self(
      String actor, Long storeId, String month, MonthlyPdfBudget budget) {
    var first = self.monthly(actor, storeId, month, 0, 1);
    var person =
        people
            .findByUserId(actors.requireUserId(actor))
            .orElseThrow(() -> new NotFoundException("報酬明細の店舗が見つかりません"));
    return collect(
        storeId,
        person.getId(),
        first.storeName(),
        query.personName(storeId, person.getId()),
        month,
        first.totalRemuneration(),
        first.orders().getTotalElements(),
        budget);
  }

  private MonthlyPdfSnapshot collect(
      Long storeId,
      Long personId,
      String storeName,
      String name,
      String month,
      long total,
      long count,
      MonthlyPdfBudget budget) {
    budget.text(storeName);
    budget.text(name);
    var generatedAt = OffsetDateTime.now(clock);
    var rows = new ArrayList<RemunerationOrderRow>();
    long sum = 0;
    // 同一 transaction 内の分割取得なので、訂正で offset の内容と合計が異なる時点へ移らない。
    for (int page = 0; rows.size() < count; page++) {
      budget.check();
      var batch =
          query.orders(storeId, personId, YearMonth.parse(month), PageRequest.of(page, 200));
      if (batch.isEmpty() || batch.getTotalElements() != count)
        throw new IllegalStateException("月次明細の件数が一致しません");
      for (var row : batch) {
        budget.text(row.orderId());
        budget.text(row.businessDate().toString());
        budget.text(row.serviceSummary());
        sum = Math.addExact(sum, row.accruedRemuneration());
        rows.add(row);
      }
    }
    if (rows.size() != count || sum != total) throw new IllegalStateException("月次明細の合計が一致しません");
    budget.check();
    return new MonthlyPdfSnapshot(storeName, name, month, generatedAt, total, rows);
  }
}
