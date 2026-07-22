package com.kizuna.auth.application;

import com.kizuna.auth.infrastructure.TokenBlacklistService;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.PlatformUserResumed;
import com.kizuna.user.domain.PlatformUserStopped;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 認証セッション（発行済み JWT が表す認証状態）の失効。 ログアウトとパスワード変更が共用する唯一の失効経路 — 資格情報を変えるユースケースは必ずここを通すこと（controller
 * で個別に組み立てない）。
 *
 * <p>スタッフ停止・再開によるユーザー単位の一括失効もここへ集約する（#403）。イベント経由にしている理由は {@link PlatformUserStopped}
 * を参照（モジュール環の回避）。
 *
 * <p>backend/CLAUDE.md の既定はモジュール間イベントを {@code @ApplicationModuleListener}（= 非同期 + イベント発行レジストリ）で
 * 受けることだが、本用途だけは同期の {@code @TransactionalEventListener} を用いる（#403 裁定）。停止は解雇・懲戒等の即時性が要る 安全制御であり、(1)
 * 非同期だと管理者が 200 を受け取った時点でまだ失効が書かれていない窓が残る、(2) 非同期リスナーの例外は ログに落ちるだけで操作者に見えない、の 2
 * 点が許容できないため。レジストリ自体は同期リスナーでも介在する（発行の記録は残る）。
 */
@Service
@RequiredArgsConstructor
public class AuthSessionService {

  private final TokenBlacklistService tokenBlacklistService;
  private final PlatformUserRepository platformUserRepository;

  /**
   * スタッフ停止イベントを受けてユーザー単位ブラックリストへ登録する。
   *
   * <p>{@code AFTER_COMMIT} で実行する理由: commit 前に Redis へ書いてしまうと「Redis 書き込みは成功したが commit は失敗した」場合に
   * ブラックリストの残渣が残り、実際には enabled=true のまま（＝罪のない）ユーザーが最長 JWT 有効期間ぶん（既定 1 時間）ログイン不能になる （再ログインで得た新しい
   * token も email 単位の鍵で同様に弾かれてしまう）。AFTER_COMMIT なら最悪でも「停止は成功したが失効の反映が ≤1 時間遅れる」だけで済み、最新 version
   * を取り直して同じ停止要求を再送すれば復旧できる（発行条件が結果語義であるため — {@code PlatformStaffService.update} 参照）。
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPlatformUserStopped(PlatformUserStopped event) {
    if (!isEnabledNow(event.email())) {
      tokenBlacklistService.blacklistUser(event.email());
    }
  }

  /** スタッフ再開イベントを受けてユーザー単位ブラックリストを解除する。AFTER_COMMIT である理由は {@link #onPlatformUserStopped} と同じ。 */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPlatformUserResumed(PlatformUserResumed event) {
    if (isEnabledNow(event.email())) {
      tokenBlacklistService.clearUser(event.email());
    }
  }

  /**
   * コミット済みの enabled を読み直す。イベントの発行条件は「そのリクエストが停止/再開を明示的に要求したか」（結果語義）であり、 発行時点の {@code PlatformUser}
   * は要求元が読んだ時点のスナップショットでしかない。並行する停止と再開が競合した場合、 その断片的なスナップショットを信じて Redis を書き換えると、コミット済みの enabled
   * とブラックリストが食い違い得る （例: 停止が先に確定したのに、それを知らない再開要求の後処理が鍵を消してしまい、停止済みユーザーの旧 JWT が復活する）。 AFTER_COMMIT
   * で確定した状態を正本として照合することで、その食い違いを構造的に排除する。
   *
   * <p>ユーザー不在は「停止相当」（false）として扱う — 判断がつかない場合は失効側へ倒す。
   */
  private boolean isEnabledNow(String email) {
    return platformUserRepository.findByEmail(email).map(PlatformUser::getEnabled).orElse(false);
  }

  /**
   * 現在のセッションを失効させる（トークンをブラックリスト登録し、SecurityContext をクリア）。
   *
   * <p>進行中のトランザクション内から呼ばれた場合は commit 後に実行する — 資格情報の変更が rollback されたのにセッションだけ失効する事故を防ぐ。
   *
   * @param authHeaderOrToken Authorization ヘッダ値または生トークン（null 可 — その場合はコンテキストのみクリア）
   */
  public void invalidate(String authHeaderOrToken) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              doInvalidate(authHeaderOrToken);
            }
          });
      return;
    }
    doInvalidate(authHeaderOrToken);
  }

  private void doInvalidate(String authHeaderOrToken) {
    tokenBlacklistService.blacklist(authHeaderOrToken);
    SecurityContextHolder.clearContext();
  }
}
