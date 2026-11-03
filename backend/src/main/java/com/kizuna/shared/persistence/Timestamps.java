package com.kizuna.shared.persistence;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * 行に書く「いつ起きたか」の時刻源（記録した時刻から数える期限も含む）。保存済みの時刻・日付と比べる「今」は {@code Clock} bean から読む。
 *
 * <p>DB（timestamptz）から読み戻る形（UTC・マイクロ秒）で作り、書き込み直後の応答と読み直しを同じ値にする。ナノ秒を残すと pgjdbc が送信時に四捨五入する。
 */
public final class Timestamps {

  private Timestamps() {}

  public static OffsetDateTime now() {
    return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
  }
}
