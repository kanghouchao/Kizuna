package com.kizuna.shared.persistence;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/** 行の created_at / updated_at の時刻源。書き込み直後の応答を読み直しと同じ値にするため、DB から読み戻る形（UTC・マイクロ秒）で作る。 */
final class RowTimestamps {

  private RowTimestamps() {}

  static OffsetDateTime now() {
    return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
  }
}
