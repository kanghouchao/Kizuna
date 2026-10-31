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
 * で弾かれてしまう。フロントエンドは token cookie があれば全リクエスト（ログイン・招待受諾を含む）に Bearer を付けるため、陳腐化した cookie
 * を持つ利用者はこれらの端点そのものへ到達できなくなる。
 *
 * <p>{@link #BEARER_EXEMPT_MATCHERS} に列挙した「未認証で叩かれることが前提の公開端点」では Bearer を一切解決せず {@code null}
 * を返し、resource-server に匿名リクエストとして継続させる（decoder/validator に到達しないため壊れた token の内容は一切見ない）。それ以外は {@link
 * DefaultBearerTokenResolver} の既定動作に委譲し、保護端点では従来どおり 壊れた Bearer が 401 になる。
 */
@Component
public class ExemptingBearerTokenResolver implements BearerTokenResolver {

  private static final RequestMatcher[] BEARER_EXEMPT_MATCHERS = {
    PathPatternRequestMatcher.withDefaults().matcher("/auth/login"),
    // 招待閲覧と受諾（新規登録・既存の利用者）。既存の利用者の受諾は本文の資格情報で本人を確かめるため、
    // 別アカウントでログイン中のブラウザが送る Bearer は見ない。
    PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/cast-invitations/view"),
    PathPatternRequestMatcher.withDefaults()
        .matcher(HttpMethod.POST, "/cast-invitations/acceptance"),
    PathPatternRequestMatcher.withDefaults()
        .matcher(HttpMethod.POST, "/cast-invitations/acceptance/existing"),
    // 会員の自助登録（POST）。統一ログイン画面からの遷移で陳腐な token cookie が付きうる。
    PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/members"),
    // LINE ログインの公開端点（設定照会・ログイン・登録確定）。統一ログイン画面からの遷移で陳腐な token cookie が付きうる。
    // 連携（/me/line）は認証必須のため対象外 — 免除すると Bearer が解決されず、常に匿名として 401 になる。
    PathPatternRequestMatcher.withDefaults().matcher("/auth/line/config"),
    PathPatternRequestMatcher.withDefaults().matcher("/auth/line/login"),
    PathPatternRequestMatcher.withDefaults().matcher("/auth/line/register"),
    PathPatternRequestMatcher.withDefaults().matcher("/stores/lookup"),
    PathPatternRequestMatcher.withDefaults().matcher("/config/public"),
    PathPatternRequestMatcher.withDefaults().matcher("/casts/public"),
    PathPatternRequestMatcher.withDefaults().matcher("/shifts/public"),
    PathPatternRequestMatcher.withDefaults()
        .matcher(HttpMethod.GET, "/order-applications/public/contact-consent"),
    PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/reviews/public"),
    // 公開サイトからの匿名 POST（ゲスト予約申請）。公開サイトを見ている来訪者は会員・キャストの陳腐な
    // token cookie を持ちうるため、免除しないと申請そのものが 401 で通らなくなる。
    PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/order-applications/public"),
    // 初期設定の登録（POST）。開発 DB を作り直した後もブラウザに旧 DB の token cookie が残りうる。
    PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/auth/initial-setup"),
    // リフレッシュとログアウトはアクセストークンの期限切れ後に呼ばれるのが普通で、期限切れの Bearer で手前の 401 に
    // されると働かない。ログアウトは Authorization を自分で読んで sid を取る。
    PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/auth/refresh"),
    PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/auth/logout"),
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
