package com.kizuna.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AppPropertiesTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(Config.class);

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1"})
  void receiptLockTimeoutThatPostgresWouldTreatAsUnlimitedFailsStartup(String seconds) {
    runner
        .withPropertyValues("app.receipt.lock-timeout-seconds=" + seconds)
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void receiptLockTimeoutBindsAPositiveValue() {
    runner
        .withPropertyValues("app.receipt.lock-timeout-seconds=3")
        .run(
            context ->
                assertThat(
                        context.getBean(AppProperties.class).getReceipt().getLockTimeoutSeconds())
                    .isEqualTo(3));
  }

  @Configuration
  @EnableConfigurationProperties(AppProperties.class)
  static class Config {}
}
