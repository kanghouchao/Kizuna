package com.kizuna.auth.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
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
 * DefaultBearerTokenResolver} の既定動作に委譲し、保護端点では従来どおり 壊れた Bearer が 401 になる。{@code /auth/logout}
 * は対象に含めない（controller が自前でヘッダを読むため resource-server の認証成否に依存せず、401 になっても実害がない）。
 */
@Component
public class ExemptingBearerTokenResolver implements BearerTokenResolver {

  private static final RequestMatcher[] BEARER_EXEMPT_MATCHERS = {
    PathPatternRequestMatcher.withDefaults().matcher("/auth/login"),
    // 招待閲覧と新規登録受諾。既存ユーザーの /acceptance/existing は ROLE_CAST を要求するため対象外
    // （2 セグメントなのでここのどの matcher にも当たらない）。
    PathPatternRequestMatcher.withDefaults().matcher("/admin/cast-invitations/view"),
    PathPatternRequestMatcher.withDefaults().matcher("/admin/cast-invitations/acceptance"),
    // 会員の自助登録（POST）。統一ログイン画面からの遷移で陳腐な token cookie が付きうる。
    PathPatternRequestMatcher.withDefaults().matcher("/admin/members"),
    // LINE ログインの公開端点（設定照会・ログイン・登録確定）。統一ログイン画面からの遷移で陳腐な token cookie が付きうる。
    // 連携（/me/line）は認証必須のため対象外 — 免除すると Bearer が解決されず、常に匿名として 401 になる。
    PathPatternRequestMatcher.withDefaults().matcher("/auth/line/config"),
    PathPatternRequestMatcher.withDefaults().matcher("/auth/line/login"),
    PathPatternRequestMatcher.withDefaults().matcher("/auth/line/register"),
    PathPatternRequestMatcher.withDefaults().matcher("/admin/stores/lookup"),
    PathPatternRequestMatcher.withDefaults().matcher("/store/config/public"),
    PathPatternRequestMatcher.withDefaults().matcher("/store/casts/public"),
    PathPatternRequestMatcher.withDefaults().matcher("/store/shifts/public"),
    PathPatternRequestMatcher.withDefaults()
        .matcher(HttpMethod.GET, "/store/order-applications/public/contact-consent"),
    // 公開店面からの匿名 POST（ゲスト予約申請）。店面を見ている来訪者は会員・キャストの陳腐な
    // token cookie を持ちうるため、免除しないと申請そのものが 401 で通らなくなる。
    PathPatternRequestMatcher.withDefaults()
        .matcher(HttpMethod.POST, "/store/order-applications/public"),
    // 初期設定の登録（POST）。開発 DB を作り直した後もブラウザに旧 DB の token cookie が残りうる。
    PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/auth/initial-setup"),
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
