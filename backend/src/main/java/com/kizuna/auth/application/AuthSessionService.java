package com.kizuna.auth.application;

import com.kizuna.auth.infrastructure.TokenBlacklistService;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.PlatformUserResumed;
import com.kizuna.user.domain.PlatformUserStopped;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 認証セッション（発行済み JWT が表す認証状態）の失効。 ログアウトとパスワード変更が共用する唯一の失効経路 — 資格情報を変えるユースケースは必ずここを通すこと（controller
 * で個別に組み立てない）。
 *
 * <p>スタッフ停止・再開によるユーザー単位の一括失効もここへ集約する（#403）。イベント経由にしている理由は {@link PlatformUserStopped}
 * を参照（モジュール環の回避）。
 *
 * <p>backend/CLAUDE.md の既定はモジュール間イベントを {@code @ApplicationModuleListener}（= 非同期 + イベント発行レジストリ）で
 * 受けることだが、本用途だけは同期の {@code @EventListener} + 手書きの commit 後同期を用いる（#403 裁定）。停止は解雇・懲戒等の
 * 即時性が要る安全制御であり、(1) 非同期だと管理者が 200 を受け取った時点でまだ失効が書かれていない窓が残る、(2) 失効の書き込みが失敗しても 操作者に伝わらない、の 2
 * 点が許容できないため。
 *
 * <p>(2) のために {@code @TransactionalEventListener(AFTER_COMMIT)} は使えない。同注釈の AFTER_COMMIT は {@code
 * TransactionSynchronization.afterCommit()} ではなく {@code afterCompletion(STATUS_COMMITTED)} から
 * 配送され（{@code TransactionalApplicationListenerSynchronization$PlatformSynchronization} は {@code
 * afterCommit()} を実装していない）、{@code TransactionSynchronizationUtils.invokeAfterCompletion} が
 * Throwable を握って ログへ落とすため、Redis 書き込みが失敗しても管理者には 200 が返る。{@code afterCommit()} を直接登録する経路 （{@code
 * invokeAfterCommit}）だけが例外を伝播させる（PR #435 codex 指摘）。
 */
@Service
public class AuthSessionService {

  private final TokenBlacklistService tokenBlacklistService;
  private final PlatformUserRepository platformUserRepository;

  /** 確定済みの行を読むための独立トランザクション（{@link #isEnabledNow} の説明を参照）。 */
  private final TransactionTemplate freshContext;

  public AuthSessionService(
      TokenBlacklistService tokenBlacklistService,
      PlatformUserRepository platformUserRepository,
      PlatformTransactionManager transactionManager) {
    this.tokenBlacklistService = tokenBlacklistService;
    this.platformUserRepository = platformUserRepository;
    this.freshContext = new TransactionTemplate(transactionManager);
    this.freshContext.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.freshContext.setReadOnly(true);
  }

  /**
   * スタッフ停止イベントを受けてユーザー単位ブラックリストへ登録する（commit 後）。
   *
   * <p>commit 後に書く理由: commit 前に Redis へ書いてしまうと「Redis 書き込みは成功したが commit は失敗した」場合に ブラックリストの残渣が残り、実際には
   * enabled=true のまま（＝罪のない）ユーザーが最長 JWT 有効期間ぶん（既定 1 時間） ログイン不能になる（再ログインで得た新しい token も email
   * 単位の鍵で同様に弾かれてしまう）。commit 後なら最悪でも 「停止は成功したが失効の反映が ≤1 時間遅れる」だけで済み、最新 version を取り直して同じ停止要求を再送すれば
   * 復旧できる（発行条件が結果語義であるため — {@code PlatformStaffService.update} 参照）。
   */
  @EventListener
  public void onPlatformUserStopped(PlatformUserStopped event) {
    afterCommit(
        () -> {
          if (!isEnabledNow(event.email())) {
            tokenBlacklistService.blacklistUser(event.email());
          }
        });
  }

  /** スタッフ再開イベントを受けてユーザー単位ブラックリストを解除する（commit 後）。理由は {@link #onPlatformUserStopped} と同じ。 */
  @EventListener
  public void onPlatformUserResumed(PlatformUserResumed event) {
    afterCommit(
        () -> {
          if (isEnabledNow(event.email())) {
            tokenBlacklistService.clearUser(event.email());
          }
        });
  }

  /**
   * 確定済みの enabled を読み直す（イベントのスナップショットには従わない）。
   *
   * <p>塞いでいるのは停止と再開が並行したときの<b>コールバック実行順の逆転</b>である。停止 → 再開の順にコミットしても、 先行する停止の commit
   * 後処理が後続の再開のコミットより後ろへずれ込むと、確定状態は enabled=true なのに停止側が 鍵を書いてしまい、有効なユーザーが TTL
   * 満了まで締め出される。逆順なら停止済みユーザーが解封される（安全上より重い）。 各コールバックが確定状態を読み直せば、どちらの順で走っても最終状態へ収束する。
   *
   * <p>独立トランザクション（{@code REQUIRES_NEW}）が必須である点に注意。commit 後処理の時点では commit 済みトランザクションの EntityManager
   * がまだスレッドに束縛されており、そのまま問い合わせると Hibernate は永続化コンテキストで管理中の （＝この要求が読んだ時点の）インスタンスを返し、行の最新値を捨ててしまう。
   *
   * <p>ユーザー不在は「停止相当」（false）として扱う — 判断がつかない場合は失効側へ倒す。
   */
  private boolean isEnabledNow(String email) {
    return Boolean.TRUE.equals(
        freshContext.execute(
            status ->
                platformUserRepository
                    .findByEmail(email)
                    .map(PlatformUser::getEnabled)
                    .orElse(false)));
  }

  /**
   * 現在のセッションを失効させる（トークンをブラックリスト登録し、SecurityContext をクリア）。
   *
   * <p>進行中のトランザクション内から呼ばれた場合は commit 後に実行する — 資格情報の変更が rollback されたのにセッションだけ失効する事故を防ぐ。
   *
   * @param authHeaderOrToken Authorization ヘッダ値または生トークン（null 可 — その場合はコンテキストのみクリア）
   */
  public void invalidate(String authHeaderOrToken) {
    afterCommit(
        () -> {
          tokenBlacklistService.blacklist(authHeaderOrToken);
          SecurityContextHolder.clearContext();
        });
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
