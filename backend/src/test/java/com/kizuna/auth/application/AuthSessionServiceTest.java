package com.kizuna.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kizuna.auth.infrastructure.CredentialVersionService;
import com.kizuna.auth.infrastructure.TokenBlacklistService;
import com.kizuna.user.domain.PlatformUserCredentialsChanged;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class AuthSessionServiceTest {

  private static final String EMAIL = "target@kizuna.test";

  @Mock private TokenBlacklistService tokenBlacklistService;

  @Mock private CredentialVersionService credentialVersionService;

  @InjectMocks private AuthSessionService service;

  @Test
  void invalidate_blacklistsTokenAndClearsContext() {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("user", "n/a"));

    service.invalidate("Bearer some-token");

    verify(tokenBlacklistService).blacklist("Bearer some-token");
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void invalidate_withinTransaction_defersUntilAfterCommit() {
    TransactionSynchronizationManager.initSynchronization();
    try {
      SecurityContextHolder.getContext()
          .setAuthentication(new TestingAuthenticationToken("user", "n/a"));

      service.invalidate("Bearer some-token");

      // commit 前は失効しない（rollback 時にセッションだけ失効する事故の防止）
      verify(tokenBlacklistService, never()).blacklist(anyString());
      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();

      TransactionSynchronizationManager.getSynchronizations()
          .forEach(TransactionSynchronization::afterCommit);

      verify(tokenBlacklistService).blacklist("Bearer some-token");
      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void invalidate_withinTransaction_rollbackDoesNotBlacklist() {
    TransactionSynchronizationManager.initSynchronization();
    try {
      service.invalidate("Bearer some-token");
      // rollback: afterCommit は発火しないまま同期が破棄される
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }

    verify(tokenBlacklistService, never()).blacklist(anyString());
  }

  @Test
  @DisplayName("資格情報変更イベント: イベントが運ぶ確定値をそのまま版キャッシュへ反映する")
  void onCredentialsChanged_reflectsCarriedVersion() {
    service.onCredentialsChanged(new PlatformUserCredentialsChanged(EMAIL, 3L));

    verify(credentialVersionService).reflect(EMAIL, 3L);
  }

  @Test
  @DisplayName("資格情報変更イベント: トランザクション内では commit 後まで反映を遅延する")
  void onCredentialsChanged_withinTransaction_defersUntilAfterCommit() {
    TransactionSynchronizationManager.initSynchronization();
    try {
      service.onCredentialsChanged(new PlatformUserCredentialsChanged(EMAIL, 3L));

      // commit 前に書くと「Redis 成功・commit 失敗」で増えていない版が残り、正当なトークンが誤拒否される
      verify(credentialVersionService, never()).reflect(anyString(), anyLong());

      TransactionSynchronizationManager.getSynchronizations()
          .forEach(TransactionSynchronization::afterCommit);

      verify(credentialVersionService).reflect(EMAIL, 3L);
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  @DisplayName("資格情報変更イベント: rollback なら反映しない")
  void onCredentialsChanged_rollbackDoesNotReflect() {
    TransactionSynchronizationManager.initSynchronization();
    try {
      service.onCredentialsChanged(new PlatformUserCredentialsChanged(EMAIL, 3L));
      // rollback: afterCommit は発火しないまま同期が破棄される
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }

    verify(credentialVersionService, never()).reflect(anyString(), anyLong());
  }
}
