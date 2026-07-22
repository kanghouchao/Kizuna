package com.kizuna.auth.application;

import com.kizuna.auth.infrastructure.TokenBlacklistService;
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
 * <p>スタッフ停止・再開によるユーザー単位の一括失効もここへ集約する（#403）。user.application は {@link PlatformUserStopped}/{@link
 * PlatformUserResumed} イベントを発行するだけで {@code TokenBlacklistService} を直接知らない — user.application が
 * auth.infrastructure を直接注入すると、既存の auth→user 依存と合わせて user↔auth の モジュール環になってしまう（{@code
 * ModularityTests} が red になる）ため、イベント経由で依存の向きを auth→user のまま保つ。
 */
@Service
@RequiredArgsConstructor
public class AuthSessionService {

  private final TokenBlacklistService tokenBlacklistService;

  /**
   * スタッフ停止イベントを受けてユーザー単位ブラックリストへ登録する。
   *
   * <p>{@code AFTER_COMMIT} で実行する理由: commit 前に Redis へ書いてしまうと「Redis 書き込みは成功したが commit は失敗した」場合に
   * ブラックリストの残渣が残り、実際には enabled=true のまま（＝罪のない）ユーザーが最長 JWT 有効期間ぶん（既定 1 時間）ログイン不能になる （再ログインで得た新しい
   * token も email 単位の鍵で同様に弾かれてしまう）。AFTER_COMMIT なら最悪でも「停止は成功したが失効の反映が ≤1
   * 時間遅れる」だけで済み、しかも同一の停止リクエストを再送するだけで復旧できる。
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPlatformUserStopped(PlatformUserStopped event) {
    tokenBlacklistService.blacklistUser(event.email());
  }

  /** スタッフ再開イベントを受けてユーザー単位ブラックリストを解除する。AFTER_COMMIT である理由は {@link #onPlatformUserStopped} と同じ。 */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPlatformUserResumed(PlatformUserResumed event) {
    tokenBlacklistService.clearUser(event.email());
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
