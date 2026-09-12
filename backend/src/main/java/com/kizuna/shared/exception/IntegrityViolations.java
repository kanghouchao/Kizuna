package com.kizuna.shared.exception;

import java.util.Map;
import java.util.function.Supplier;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Hibernate の制約名を等値照合して業務例外へ写像する。文言はドライバやロケールに依存するため照合しない。 操作によって適切な業務例外が異なるため、対応表と保存・flush
 * の選択は呼出側が担う。 明示 flush は永続化コンテキストの変更に伴う違反を捕捉範囲内で顕在化させる。トランザクションや再試行は管理しない。
 * 未対応の違反は元の例外を保ち、全域ハンドラの分類に委ねる。
 */
public final class IntegrityViolations {

  private IntegrityViolations() {}

  public static <T> T translateOnFailure(
      Supplier<T> operation, Map<DbConstraint, Supplier<RuntimeException>> table) {
    try {
      return operation.get();
    } catch (DataIntegrityViolationException ex) {
      throw translate(ex, table);
    }
  }

  /** 違反した制約に対応する業務例外を返す。対応が無い場合・制約名を取れない場合は {@code ex} 自身を返す。 */
  public static RuntimeException translate(
      DataIntegrityViolationException ex, Map<DbConstraint, Supplier<RuntimeException>> table) {
    String violated = violatedConstraintName(ex);
    if (violated == null) {
      return ex;
    }
    for (Map.Entry<DbConstraint, Supplier<RuntimeException>> mapping : table.entrySet()) {
      if (mapping.getKey().sqlName().equals(violated)) {
        return mapping.getValue().get();
      }
    }
    return ex;
  }

  /**
   * 違反した制約が {@code constraint} かどうか。例外を投げ替えるのではなく、違反の種別で処理を分岐したい呼出側 （冪等キーの競合敗者を再送処理へ回す等）のための問い口。
   */
  public static boolean violates(DataIntegrityViolationException ex, DbConstraint constraint) {
    return constraint.sqlName().equals(violatedConstraintName(ex));
  }

  /**
   * 違反した制約の名前を取り出す。Spring の変換で包まれた層数は経路によって異なるため、原因連鎖を辿って Hibernate の例外を探す（最深層は JDBC
   * ドライバの例外であり、制約名を型で持たない）。
   *
   * @return 制約名。連鎖に Hibernate の整合性違反が無い場合、または DB が制約名を報告しなかった場合は null
   */
  private static String violatedConstraintName(DataIntegrityViolationException ex) {
    for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConstraintViolationException violation) {
        return violation.getConstraintName();
      }
    }
    return null;
  }
}
