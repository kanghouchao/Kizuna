package com.kizuna.shared.web;

import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.IntegrityViolations;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 同時再送で操作キーの一意制約に敗れた要求だけを、既存の結果を読み直す再送処理へ回す（ADR 0007 / 0037）。
 *
 * <p>controller から呼び、サービスのトランザクション境界の外で捕捉する — 違反の時点で敗者のトランザクションは取消済みで、内側では読み直せない。
 * 他の整合性違反は実装欠陥なのでそのまま上げ、全域ハンドラの分類に委ねる。
 */
public final class Replays {
  private Replays() {}

  public static <T> T write(DbConstraint key, Supplier<T> write, Supplier<T> replay) {
    try {
      return write.get();
    } catch (DataIntegrityViolationException error) {
      if (!IntegrityViolations.violates(error, key)) throw error;
      return replay.get();
    }
  }
}
