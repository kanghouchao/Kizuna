package com.kizuna.order.application;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceUnavailableException;
import jakarta.persistence.QueryTimeoutException;
import java.sql.SQLException;

public final class MonthlyPdfBudget {
  private final long started = System.nanoTime();
  private final long timeoutNanos;
  private final long maxCharacters;
  private final long maxBytes;
  private long characters;

  public MonthlyPdfBudget(AppProperties.MonthlyPdf settings) {
    timeoutNanos = Math.multiplyExact(settings.getTimeoutSeconds(), 1_000_000_000L);
    maxCharacters = settings.getMaxCharacters();
    maxBytes = settings.getMaxBytes();
    if (timeoutNanos <= 0 || maxCharacters <= 0 || maxBytes <= 0)
      throw new IllegalArgumentException("PDF の資源設定は正数が必要です");
  }

  public void check() {
    if (Thread.currentThread().isInterrupted() || System.nanoTime() - started >= timeoutNanos)
      throw new ServiceUnavailableException("PDF の生成が時間内に完了しませんでした。再試行してください");
  }

  public void text(String value) {
    check();
    characters += value.codePointCount(0, value.length());
    if (characters > maxCharacters) throw tooLarge();
  }

  public void bytes(long count) {
    check();
    if (count > maxBytes) throw tooLarge();
  }

  static boolean readTimedOut(Throwable failure) {
    for (var cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof QueryTimeoutException) return true;
      if (cause instanceof SQLException sql && "57014".equals(sql.getSQLState())) return true;
    }
    return false;
  }

  private ServiceUnavailableException tooLarge() {
    return new ServiceUnavailableException("対象明細が大きいため PDF を生成できませんでした");
  }
}
