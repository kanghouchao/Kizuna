package com.kizuna.shared.persistence;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * アプリが作って保存する時刻の時刻源。DB から読み戻る形（UTC・マイクロ秒）で作る。
 *
 * <p>ナノ秒を残すと pgjdbc が送信時に四捨五入し、書き込み直後の応答と読み直しが食い違う。業務の時計で読む時刻は {@code Clock} bean が同じ精度で刻む。
 */
public final class Timestamps {

  private Timestamps() {}

  public static OffsetDateTime now() {
    return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
  }
}
