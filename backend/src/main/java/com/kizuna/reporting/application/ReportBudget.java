package com.kizuna.reporting.application;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceUnavailableException;
import jakarta.persistence.QueryTimeoutException;
import java.io.ByteArrayOutputStream;

public final class ReportBudget {
  private final long started = System.nanoTime();
  private final AppProperties.OperationalReport settings;
  private long characters;

  public ReportBudget(AppProperties.OperationalReport settings) {
    this.settings = settings;
    if (settings.getTimeoutSeconds() <= 0
        || settings.getMaxCharacters() <= 0
        || settings.getMaxBytes() <= 0) throw new IllegalArgumentException("帳票の資源設定は正数が必要です");
  }

  public static boolean persistenceTimedOut(Throwable failure) {
    return failure instanceof QueryTimeoutException;
  }

  public void check() {
    if (Thread.currentThread().isInterrupted()
        || System.nanoTime() - started >= settings.getTimeoutSeconds() * 1_000_000_000L)
      throw new ServiceUnavailableException("集計が時間内に完了しませんでした。条件を絞って再試行してください");
  }

  public void text(String text) {
    check();
    characters += text.length();
    if (text.length() > 32767 || characters > settings.getMaxCharacters()) throw tooLarge();
  }

  public ByteArrayOutputStream output() {
    return new ByteArrayOutputStream() {
      @Override
      public synchronized void write(int value) {
        ensure(1);
        super.write(value);
      }

      @Override
      public synchronized void write(byte[] bytes, int offset, int length) {
        ensure(length);
        super.write(bytes, offset, length);
      }

      private void ensure(int added) {
        check();
        if ((long) count + added > settings.getMaxBytes()) throw tooLarge();
      }
    };
  }

  private ServiceUnavailableException tooLarge() {
    return new ServiceUnavailableException("帳票が大きすぎます。期間または店舗を絞ってください");
  }
}
