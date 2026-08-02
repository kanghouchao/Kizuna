package com.kizuna.auth.infrastructure;

import com.kizuna.settings.application.LineChannelSettings;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.config.AppProperties;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * LINE チャネル資格情報の解決。システム設定（DB）を優先し、未設定のときだけ環境変数由来の設定（{@code app.line.*}）へフォールバックする — SMTP
 * と同じ優先順位で、運用中の管理画面からの差し替えを再デプロイなしに効かせるため。
 *
 * <p>ID とシークレットの双方が揃わなければ「未設定」とみなす（片方だけでは LINE と通信できず、公開端点が enabled=true を返してしまうため）。
 */
@Component
@RequiredArgsConstructor
public class LineChannelResolver {

  private final SystemConfigService systemConfigService;
  private final AppProperties appProperties;

  /** 解決済みチャネル。両方の資格情報が揃わなければ空（＝LINE ログイン無効）。 */
  public Optional<LineChannel> resolve() {
    LineChannelSettings stored = systemConfigService.lineChannelSettings();
    if (stored.configured()) {
      return Optional.of(new LineChannel(stored.channelId(), stored.channelSecret()));
    }
    String channelId = appProperties.getLine().getChannelId();
    String channelSecret = appProperties.getLine().getChannelSecret();
    if (StringUtils.hasText(channelId) && StringUtils.hasText(channelSecret)) {
      return Optional.of(new LineChannel(channelId, channelSecret));
    }
    return Optional.empty();
  }
}
