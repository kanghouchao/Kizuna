package com.kizuna.auth.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

/**
 * 公開端点（{@link #EXEMPTIONS}）では Bearer を解決せず匿名の要求として通し、それ以外は {@link DefaultBearerTokenResolver} に委ねる。
 *
 * <p>resource-server は認可の判定より前に Bearer を検証するため、タブのメモリに残った陳腐なトークンが公開端点まで 401 にしてしまう。
 */
@Component
public class ExemptingBearerTokenResolver implements BearerTokenResolver {

  /** Bearer を解決しない端点。{@code method} が {@code null} の行は全メソッドを免除する。 */
  record Exemption(HttpMethod method, String pattern) {}

  static final List<Exemption> EXEMPTIONS =
      List.of(
          new Exemption(null, "/auth/login"),
          // 招待閲覧と受諾（新規登録・既存の利用者）。既存の利用者の受諾は本文の資格情報で本人を確かめるため、
          // 別アカウントでログイン中のタブが送る Bearer は見ない。
          new Exemption(HttpMethod.POST, "/cast-invitations/view"),
          new Exemption(HttpMethod.POST, "/cast-invitations/acceptance"),
          new Exemption(HttpMethod.POST, "/cast-invitations/acceptance/existing"),
          // 会員の自助登録（POST）。統一ログイン画面からの遷移で陳腐なアクセストークンが付きうる。
          new Exemption(HttpMethod.POST, "/members"),
          // LINE ログインの公開端点（設定照会・ログイン・登録確定）。統一ログイン画面からの遷移で陳腐なアクセストークンが付きうる。
          // 連携（/me/line）は認証必須のため対象外 — 免除すると Bearer が解決されず、常に匿名として 401 になる。
          new Exemption(null, "/auth/line/config"),
          new Exemption(null, "/auth/line/login"),
          new Exemption(null, "/auth/line/register"),
          new Exemption(null, "/stores/lookup"),
          new Exemption(null, "/config/public"),
          new Exemption(null, "/casts/public"),
          new Exemption(null, "/shifts/public"),
          new Exemption(HttpMethod.GET, "/order-applications/public/contact-consent"),
          new Exemption(HttpMethod.GET, "/reviews/public"),
          new Exemption(HttpMethod.GET, "/articles/public"),
          new Exemption(HttpMethod.GET, "/articles/public/{id}"),
          // 公開サイトからの匿名 POST（ゲスト予約申請）。公開サイトを見ている来訪者は会員・キャストの陳腐な
          // アクセストークンを持ちうるため、免除しないと申請そのものが 401 で通らなくなる。
          new Exemption(HttpMethod.POST, "/order-applications/public"),
          // 初期設定の登録（POST）。開発 DB を作り直す前から開いたままのタブは、旧 DB のアクセストークンを持ちうる。
          new Exemption(HttpMethod.POST, "/auth/initial-setup"),
          // リフレッシュとログアウトはアクセストークンの期限切れ後に呼ばれるのが普通で、期限切れの Bearer で手前の 401 に
          // されると働かない。ログアウトは Authorization を自分で読んで sid を取る。
          new Exemption(HttpMethod.POST, "/auth/refresh"),
          new Exemption(HttpMethod.POST, "/auth/logout"));

  private static final List<RequestMatcher> EXEMPT_MATCHERS =
      EXEMPTIONS.stream()
          .<RequestMatcher>map(
              e -> PathPatternRequestMatcher.withDefaults().matcher(e.method(), e.pattern()))
          .toList();

  private final BearerTokenResolver delegate = new DefaultBearerTokenResolver();

  @Override
  public String resolve(HttpServletRequest request) {
    for (RequestMatcher matcher : EXEMPT_MATCHERS) {
      if (matcher.matches(request)) {
        return null;
      }
    }
    return delegate.resolve(request);
  }
}
