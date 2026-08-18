package com.kizuna.shared.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 業務判定用の時計。 */
@Configuration
public class ClockConfig {

  /**
   * 「今」を業務のタイムゾーン（{@code app.timezone}）で読む時計。営業日の判定が唯一の利用者で、 発行済みトークンの有効期限や行の created_at
   * はここを経由しない（JVM 既定の時刻を直接読む）。
   *
   * <p>時計を差し替え可能にしているのは、営業日の境界（深夜帯）を検証で再現するため。
   */
  @Bean
  Clock clock(AppProperties appProperties) {
    return Clock.system(ZoneId.of(appProperties.getTimezone()));
  }
}
