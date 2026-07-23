package com.kizuna.auth.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

/**
 * resource-server の認証フィルタは decoder より前段で全リクエストの {@code Authorization} ヘッダを見るため、匿名で叩かれるべき
 * 端点でも壊れた（期限切れ・無効署名・ブラックリスト済み）Bearer が付いていると、{@code @PreAuthorize}/{@code @PermitAll} の区別が及ぶ前に 401
 * で弾かれてしまう。前端は token cookie があれば全リクエスト（ログイン・招待受諾を含む）に Bearer を付けるため、陳腐化した cookie
 * を持つ利用者はこれらの端点そのものへ到達できなくなる。
 *
 * <p>{@link #BEARER_EXEMPT_MATCHERS} に列挙した「未認証で叩かれることが前提の公開端点」では Bearer を一切解決せず {@code null}
 * を返し、resource-server に匿名リクエストとして継続させる（decoder/validator に到達しないため壊れた token の内容は一切見ない）。それ以外は {@link
 * DefaultBearerTokenResolver} の既定動作に委譲し、保護端点では従来どおり 壊れた Bearer が 401 になる。{@code /platform/logout}
 * は対象に含めない（controller が自前でヘッダを読むため resource-server の認証成否に依存せず、401 になっても実害がない）。
 */
@Component
public class PlatformBearerTokenResolver implements BearerTokenResolver {

  private static final RequestMatcher[] BEARER_EXEMPT_MATCHERS = {
    PathPatternRequestMatcher.withDefaults().matcher("/platform/login"),
    // 招待閲覧（GET /{token}）と新規登録受諾（POST /{token}/acceptance）。既存ユーザーの
    // /acceptance/existing は ROLE_CAST を要求するため対象外（別 matcher で拾われない）。
    PathPatternRequestMatcher.withDefaults().matcher("/platform/cast-invitations/*"),
    PathPatternRequestMatcher.withDefaults().matcher("/platform/cast-invitations/*/acceptance"),
    PathPatternRequestMatcher.withDefaults().matcher("/platform/stores/lookup"),
    PathPatternRequestMatcher.withDefaults().matcher("/store/config/public"),
    PathPatternRequestMatcher.withDefaults().matcher("/store/casts/public"),
    PathPatternRequestMatcher.withDefaults().matcher("/store/shifts/public"),
  };

  private final BearerTokenResolver delegate = new DefaultBearerTokenResolver();

  @Override
  public String resolve(HttpServletRequest request) {
    for (RequestMatcher matcher : BEARER_EXEMPT_MATCHERS) {
      if (matcher.matches(request)) {
        return null;
      }
    }
    return delegate.resolve(request);
  }
}
