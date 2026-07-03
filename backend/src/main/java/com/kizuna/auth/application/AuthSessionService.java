package com.kizuna.auth.application;

import com.kizuna.auth.infrastructure.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 認証セッション（発行済み JWT が表す認証状態）の失効。 ログアウトとパスワード変更が共用する唯一の失効経路 — 資格情報を変えるユースケースは必ずここを通すこと（controller
 * で個別に組み立てない）。
 */
@Service
@RequiredArgsConstructor
public class AuthSessionService {

  private final TokenBlacklistService tokenBlacklistService;

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
