package com.kizuna.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.kizuna.settings.application.SystemConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * SMTP 設定の読み取りが実インフラ（実 Redis）越しでも成立することを固定する IT。
 *
 * <p>キャッシュ値の既定シリアライザは JDK 直列化で、{@code Serializable} でない値をキャッシュに載せようとすると
 * 読み取り自体が例外になる（呼び出し側が握り潰すと「メールが一切送信されない」静かな故障になる）。 単体テストはキャッシュプロキシを経由しないため、この断言は IT でしか固定できない。
 */
@SpringBootTest
class SmtpSettingsReadIT {

  @Autowired private SystemConfigService systemConfigService;

  @Test
  @DisplayName("smtpSettings は繰り返し呼んでも例外にならず、一貫した値を返すこと")
  void smtpSettingsIsReadableThroughRealInfrastructure() {
    assertThatCode(() -> systemConfigService.smtpSettings()).doesNotThrowAnyException();
    assertThat(systemConfigService.smtpSettings()).isEqualTo(systemConfigService.smtpSettings());
  }
}
