package com.kizuna.advertising.application;

import com.kizuna.shared.exception.ServiceUnavailableException;
import com.kizuna.shared.export.DocumentBudget;
import java.sql.SQLException;
import java.util.function.Supplier;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.TransactionTimedOutException;

public final class AdvertisingFailures {
  private AdvertisingFailures() {}

  public static <T> T run(Supplier<T> action) {
    try {
      return action.get();
    } catch (RuntimeException ex) {
      for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
        if (cause instanceof ServiceUnavailableException unavailable) throw unavailable;
        if (cause instanceof QueryTimeoutException
            || DocumentBudget.persistenceTimedOut(cause)
            || cause instanceof TransactionTimedOutException
            || (cause instanceof SQLException sql && "57014".equals(sql.getSQLState())))
          throw new ServiceUnavailableException("広告費の処理が時間内に完了しませんでした。同じ要求で再試行してください");
      }
      throw ex;
    }
  }
}
