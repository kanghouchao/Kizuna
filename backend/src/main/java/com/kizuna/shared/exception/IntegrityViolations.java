package com.kizuna.shared.exception;

import java.util.Map;
import java.util.function.Supplier;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 整合性違反を、違反した制約の名前で業務例外へ写像する共通抽出。
 *
 * <p>制約名は Hibernate の {@link ConstraintViolationException#getConstraintName()}
 * から取り、字面と等値比較する。ドライバの報錯文言への 部分一致は使わない — 文言はドライバとロケールに依存し、別種の違反（列長超過など）を偶然含んでしまえば誤った業務例外へ帰属する。
 *
 * <p>「どの制約をどの業務例外へ写像するか」の決定は呼出側に残す。同じ制約でも操作の向きによって適切な分類は異なる（授与中ロールの削除は競合、 存在しないロールの授与は要求誤り）ため、対応表は
 * call site が持つ。
 *
 * <p>写像に無い違反は元の例外をそのまま返す（fail-loud）。呼出側が {@code throw} することで、全域ハンドラの分類（一意違反のみ 409、他は 500）へ落ちる。
 */
public final class IntegrityViolations {

  private IntegrityViolations() {}

  /**
   * 違反した制約に対応する業務例外を返す。対応が無い場合・制約名を取れない場合は {@code ex} 自身を返す。
   *
   * @param ex 整合性違反
   * @param table 制約から業務例外の生成への対応表
   * @return 呼出側が送出すべき例外
   */
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
