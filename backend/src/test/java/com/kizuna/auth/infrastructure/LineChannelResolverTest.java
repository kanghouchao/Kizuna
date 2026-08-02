package com.kizuna.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.kizuna.settings.application.LineChannelSettings;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link LineChannelResolver} の単体テスト。 */
@ExtendWith(MockitoExtension.class)
class LineChannelResolverTest {

  @Mock private SystemConfigService systemConfigService;

  private final AppProperties appProperties = new AppProperties();

  private LineChannelResolver resolver;

  @BeforeEach
  void setUp() {
    resolver = new LineChannelResolver(systemConfigService, appProperties);
  }

  private void stubStored(String channelId, String channelSecret) {
    when(systemConfigService.lineChannelSettings())
        .thenReturn(new LineChannelSettings(channelId, channelSecret));
  }

  private void envChannel(String channelId, String channelSecret) {
    appProperties.getLine().setChannelId(channelId);
    appProperties.getLine().setChannelSecret(channelSecret);
  }

  @Test
  @DisplayName("システム設定に両方あればそれを使う（環境変数より優先）")
  void storedSettingsWinOverEnvironment() {
    stubStored("db-id", "db-secret");
    envChannel("env-id", "env-secret");

    assertThat(resolver.resolve()).contains(new LineChannel("db-id", "db-secret"));
  }

  @Test
  @DisplayName("システム設定が空なら環境変数へフォールバックする")
  void fallsBackToEnvironmentWhenStoredIsEmpty() {
    stubStored("", "");
    envChannel("env-id", "env-secret");

    assertThat(resolver.resolve()).contains(new LineChannel("env-id", "env-secret"));
  }

  @Test
  @DisplayName("システム設定が片方だけなら未設定とみなし環境変数へフォールバックする（片方では LINE と通信できない）")
  void partialStoredSettingsFallBackToEnvironment() {
    stubStored("db-id", "");
    envChannel("env-id", "env-secret");

    assertThat(resolver.resolve()).contains(new LineChannel("env-id", "env-secret"));
  }

  @Test
  @DisplayName("双方とも未設定なら空を返す（LINE ログイン無効）")
  void emptyWhenNothingConfigured() {
    stubStored("", "");
    envChannel(null, null);

    assertThat(resolver.resolve()).isEmpty();
  }

  @Test
  @DisplayName("環境変数が片方だけなら空を返す（enabled=true を返してしまわない）")
  void emptyWhenEnvironmentIsPartial() {
    stubStored("", "");
    envChannel("env-id", "  ");

    assertThat(resolver.resolve()).isEmpty();
  }
}
