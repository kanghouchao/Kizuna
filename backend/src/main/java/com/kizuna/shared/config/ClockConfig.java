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
   * 「今」を業務のタイムゾーン（{@code app.timezone}）で読む時計。営業日・特典付与日など業務上の「日」の判定が読む。 発行済みトークンの有効期限や行の created_at
   * はここを経由しない。
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
