package com.kizuna.shared.config;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 業務判定用の時計。 */
@Configuration
public class ClockConfig {

  /**
   * 「今」を業務のタイムゾーン（{@code app.timezone}）で読む時計。期限・営業日など、保存済みの時刻・日付と比べる判定が読む。
   *
   * <p>行に書く時刻と発行済み JWT の有効期限はここを経由しない。
   *
   * <p>時計を差し替え可能にしているのは、日付の境界（深夜帯）を検証で再現するため。刻みを DB（timestamptz）の精度であるマイクロ秒に
   * 揃え、ここで作った時刻が保存の前後で同じ瞬間を指すようにする。
   */
  @Bean
  Clock clock(AppProperties appProperties) {
    return Clock.tick(
        Clock.system(ZoneId.of(appProperties.getTimezone())), Duration.ofNanos(1_000));
  }
}
