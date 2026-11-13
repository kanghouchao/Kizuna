package com.kizuna.dashboard.application;

import com.kizuna.reporting.dashboard.StoreSalesReader;
import com.kizuna.settings.application.BusinessDateService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SalesService {
  private final BusinessDateService businessDates;
  private final StoreSalesReader sales;

  // 今月と前月同時点を一つのスナップショットで読み、二つの数字の間に完了が割り込まないようにする。
  @Transactional(
      readOnly = true,
      isolation = Isolation.REPEATABLE_READ,
      timeoutString = "#{@appProperties.operationalReport.readTimeoutSeconds}")
  public Sales read(Long storeId) {
    var today = businessDates.currentBusinessDate();
    var month = sales.read(storeId, today.withDayOfMonth(1), today);
    var previous = sales.read(storeId, previousMonthStart(today), previousMonthSameDay(today));
    return new Sales(
        today, month.daily().getLast().totals(), month.total(), previous.total(), month.daily());
  }

  private static LocalDate previousMonthStart(LocalDate day) {
    return day.minusMonths(1).withDayOfMonth(1);
  }

  /** minusMonths は前月に同じ日が無ければ前月末に丸める（3/31 → 2 月末）。 */
  static LocalDate previousMonthSameDay(LocalDate day) {
    return day.minusMonths(1);
  }
}
