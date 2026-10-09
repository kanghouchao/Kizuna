package com.kizuna.reporting.application;

import com.kizuna.reporting.api.dto.OperationalReportResponse;
import com.kizuna.reporting.domain.OperationalReport;
import com.kizuna.reporting.domain.ReportCriteria;
import com.kizuna.reporting.infrastructure.ReportRenderer;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.ServiceUnavailableException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.Semaphore;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionTimedOutException;

@Service
@RequiredArgsConstructor
public class OperationalReportService {
  private final ReportSnapshot snapshot;
  private final ReportRenderer renderer;
  private final AppProperties properties;
  private final Semaphore slot = new Semaphore(1);

  public OperationalReportResponse view(
      boolean platform,
      Long storeId,
      String from,
      String to,
      String groupBy,
      int page,
      int size,
      boolean includeRemuneration) {
    if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE)
      throw new ServiceException("ページ番号と件数（1〜100）の指定が不正です");
    try {
      return execute(
          platform,
          storeId,
          from,
          to,
          groupBy,
          includeRemuneration,
          (report, budget) -> OperationalReportResponse.from(report, page, size));
    } catch (IOException ex) {
      throw new IllegalStateException(ex);
    }
  }

  public byte[] export(
      boolean platform,
      Long storeId,
      String from,
      String to,
      String groupBy,
      String format,
      boolean includeRemuneration)
      throws IOException {
    if (!"csv".equals(format) && !"xlsx".equals(format))
      throw new ServiceException("出力形式は csv / xlsx で指定してください");
    return execute(
        platform,
        storeId,
        from,
        to,
        groupBy,
        includeRemuneration,
        (report, budget) -> renderer.render(report, format, budget));
  }

  private <T> T execute(
      boolean platform,
      Long storeId,
      String from,
      String to,
      String groupBy,
      boolean includeRemuneration,
      Render<T> render)
      throws IOException {
    var criteria = ReportCriteria.parse(from, to, groupBy);
    if (!slot.tryAcquire()) throw new ServiceUnavailableException("集計を生成中です。しばらくしてから再試行してください");
    try {
      var budget = new ReportBudget(properties.getOperationalReport());
      var report =
          platform
              ? snapshot.platform(storeId, criteria, includeRemuneration)
              : snapshot.store(criteria, includeRemuneration);
      var facts = report.facts();
      if (facts.orders().size() > properties.getOperationalReport().getMaxOrders()
          || facts.orders().size() > 100_000
          || facts.stores().size() > properties.getOperationalReport().getMaxStores())
        throw new ServiceUnavailableException("対象件数が多すぎます。条件を絞ってください");
      if (facts.orders().stream()
          .anyMatch(order -> order.totalFee() < 0 || order.remuneration() < 0))
        throw new ServiceUnavailableException("受注金額を確認できません。管理者へお問い合わせください");
      facts.stores().forEach(store -> budget.text(store.storeName()));
      facts.orders().forEach(order -> budget.text(order.orderId()));
      if (report.remunerationFacts() != null) {
        report.remunerationFacts().days().forEach(day -> budget.text(day.toString()));
        report.remunerationFacts().bonuses().forEach(bonus -> budget.text(bonus.toString()));
      }
      budget.check();
      T result = render.apply(report, budget);
      budget.check();
      return result;
    } catch (RuntimeException ex) {
      for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
        if (cause instanceof ServiceUnavailableException unavailable) throw unavailable;
        if (cause instanceof QueryTimeoutException
            || ReportBudget.persistenceTimedOut(cause)
            || cause instanceof TransactionTimedOutException
            || (cause instanceof SQLException sql && "57014".equals(sql.getSQLState())))
          throw new ServiceUnavailableException("集計が時間内に完了しませんでした。条件を絞って再試行してください");
      }
      throw ex;
    } finally {
      slot.release();
    }
  }

  private interface Render<T> {
    T apply(OperationalReport report, ReportBudget budget) throws IOException;
  }
}
