package com.kizuna.order.application;

import com.kizuna.order.infrastructure.MonthlyPdfRenderer;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceUnavailableException;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionTimedOutException;

@Service
@RequiredArgsConstructor
public class MonthlyPdfService {
  private final MonthlyPdfRenderer renderer;
  private final AppProperties properties;
  private final Semaphore slot = new Semaphore(1);

  public byte[] generate(Function<MonthlyPdfBudget, MonthlyPdfSnapshot> read) throws IOException {
    if (!slot.tryAcquire()) throw new ServiceUnavailableException("PDF を生成中です。しばらくしてから再試行してください");
    try {
      var budget = new MonthlyPdfBudget(properties.getMonthlyPdf());
      var snapshot = read.apply(budget);
      budget.check();
      var result = renderer.render(snapshot, budget);
      budget.check();
      return result;
    } catch (RuntimeException ex) {
      if (ex instanceof QueryTimeoutException
          || ex instanceof TransactionTimedOutException
          || MonthlyPdfBudget.readTimedOut(ex))
        throw new ServiceUnavailableException("PDF の取得が時間内に完了しませんでした。再試行してください");
      throw ex;
    } finally {
      slot.release();
    }
  }
}
