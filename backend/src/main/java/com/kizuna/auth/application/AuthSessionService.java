package com.kizuna.auth.application;

import com.kizuna.auth.infrastructure.CredentialVersionService;
import com.kizuna.auth.infrastructure.SessionTokenDecoder;
import com.kizuna.auth.infrastructure.TokenBlacklistService;
import com.kizuna.user.domain.UserCredentialsChanged;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 認証セッション（発行済み JWT が表す認証状態）の失効。ログアウト（トークン単位）と、資格情報の版の変更 （パスワード変更・再設定・停止・実効権限の変更 — アカウント単位、ADR
 * 0022）が共用する唯一の失効経路。資格情報を変える ユースケースは必ず {@link UserCredentialsChanged} を発行してここを通すこと（controller
 * で個別に組み立てない）。
 *
 * <p>backend/CLAUDE.md の既定はモジュール間イベントを {@code @ApplicationModuleListener}（= 非同期 + イベント発行レジストリ）で
 * 受けることだが、本用途だけは同期の {@code @EventListener} + 手書きの commit 後同期を用いる。停止・再設定は 即時性が要る安全制御であり、(1)
 * 非同期だと管理者が 200 を受け取った時点でまだ失効が書かれていない窓が残る、(2) 失効の書き込みが失敗しても 操作者に伝わらない、の 2 点が許容できないため。
 *
 * <p>(2) のために {@code @TransactionalEventListener(AFTER_COMMIT)} は使えない。同注釈の AFTER_COMMIT は {@code
 * TransactionSynchronization.afterCommit()} ではなく {@code afterCompletion(STATUS_COMMITTED)} から
 * 配送され（{@code TransactionalApplicationListenerSynchronization$PlatformSynchronization} は {@code
 * afterCommit()} を実装していない）、{@code TransactionSynchronizationUtils.invokeAfterCompletion} が
 * Throwable を握って ログへ落とすため、Redis 書き込みが失敗しても管理者には 200 が返る。{@code afterCommit()} を直接登録する経路 （{@code
 * invokeAfterCommit}）だけが例外を伝播させる。
 */
@Service
@RequiredArgsConstructor
public class AuthSessionService {

  private final TokenBlacklistService tokenBlacklistService;
  private final CredentialVersionService credentialVersionService;
  private final SessionTokenDecoder sessionTokenDecoder;

  /**
   * 資格情報の版の変更イベントを受けて、確定済みの版を版キャッシュへ反映する（commit 後）。
   *
   * <p>commit 後に書く理由: commit 前に書いてしまうと「Redis 書き込みは成功したが commit は失敗した」場合に 増えていない版がキャッシュへ残り、実際には
   * 旧版のままの（＝罪のない）ユーザーの正当なトークンが最長 TTL ぶん拒否される。 commit 後なら最悪でも「変更は成功したが反映が遅れる」だけで済み、同じ要求の再送で書き直せる。
   *
   * <p>イベントが運ぶ確定値をそのまま単調書込みするため、並行する変更の callback 実行順が commit 順と 逆転しても最大の版へ収束する。再開は失効機構に何も書かないので、
   * 逆向きの書き込みと競合することも無く、確定状態の読み直しは要らない。
   */
  @EventListener
  public void onCredentialsChanged(UserCredentialsChanged event) {
    afterCommit(() -> credentialVersionService.reflect(event.userId(), event.credentialVersion()));
  }

  /**
   * ログアウト。Bearer のトークンと、要求が名乗る本人のセッション（sid）を失効させる。sid は Bearer から取り（期限切れでも読む）、Bearer が 無いときだけ
   * cookie から取る — 別タブで別アカウントにログインした後の旧タブのログアウトで、cookie に入っている新しいアカウントのセッションを止めないため。
   *
   * @return cookie を消してよいか（cookie の sid が失効させた sid と同じとき）
   */
  public boolean logout(String authHeader, String refreshToken) {
    Optional<String> cookieSessionId = sessionTokenDecoder.sessionIdOf(refreshToken);
    Optional<String> sessionId =
        authHeader != null && authHeader.startsWith("Bearer ")
            ? sessionTokenDecoder.sessionIdOf(authHeader.substring(7))
            : cookieSessionId;
    tokenBlacklistService.blacklist(authHeader);
    sessionId.ifPresent(tokenBlacklistService::blacklistSession);
    SecurityContextHolder.clearContext();
    return sessionId.isPresent() && sessionId.equals(cookieSessionId);
  }

  /**
   * commit 後に実行する（トランザクション外なら即時実行）。
   *
   * <p>{@code afterCommit()} を直接登録するのは例外を呼び出し側へ伝播させるためである（クラス Javadoc 参照）。失効は安全制御であり、
   * 書き込みに失敗したまま成功応答を返してはならない。
   */
  private void afterCommit(Runnable action) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              action.run();
            }
          });
      return;
    }
    action.run();
  }
}
