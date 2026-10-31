package com.kizuna.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.auth.infrastructure.CredentialVersionService;
import com.kizuna.auth.infrastructure.SessionTokenDecoder;
import com.kizuna.auth.infrastructure.TokenBlacklistService;
import com.kizuna.user.domain.UserCredentialsChanged;
import java.util.Optional;
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

  private static final long USER_ID = 42L;

  @Mock private TokenBlacklistService tokenBlacklistService;

  @Mock private CredentialVersionService credentialVersionService;

  @Mock private SessionTokenDecoder sessionTokenDecoder;

  @InjectMocks private AuthSessionService service;

  @Test
  void committedReflectionFailurePropagates() {
    var failure = new IllegalStateException("Redis 書込み失敗");
    doThrow(failure).when(credentialVersionService).reflect(USER_ID, 3L);
    TransactionSynchronizationManager.initSynchronization();
    try {
      service.onCredentialsChanged(new UserCredentialsChanged(USER_ID, 3L));
      verify(credentialVersionService, never()).reflect(anyLong(), anyLong());
      assertThatThrownBy(
              () ->
                  TransactionSynchronizationManager.getSynchronizations()
                      .forEach(TransactionSynchronization::afterCommit))
          .isSameAs(failure);
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  @DisplayName("ログアウト: Bearer の sid を失効させ、cookie が同じセッションなら消してよいと答える")
  void logout_revokesBearerSessionAndClearsMatchingCookie() {
    when(sessionTokenDecoder.sessionIdOf("access")).thenReturn(Optional.of("s-1"));
    when(sessionTokenDecoder.sessionIdOf("refresh")).thenReturn(Optional.of("s-1"));
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("user", "n/a"));

    assertThat(service.logout("Bearer access", "refresh")).isTrue();

    verify(tokenBlacklistService).blacklist("Bearer access");
    verify(tokenBlacklistService).blacklistSession("s-1");
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  @DisplayName("ログアウト: cookie が別の利用者のセッションなら、Bearer の本人だけを止めて cookie は残す")
  void logout_keepsAnotherUsersCookie() {
    when(sessionTokenDecoder.sessionIdOf("access")).thenReturn(Optional.of("s-old"));
    when(sessionTokenDecoder.sessionIdOf("refresh")).thenReturn(Optional.of("s-new"));

    assertThat(service.logout("Bearer access", "refresh")).isFalse();

    verify(tokenBlacklistService).blacklistSession("s-old");
    verify(tokenBlacklistService, never()).blacklistSession("s-new");
  }

  @Test
  @DisplayName("ログアウト: Bearer が無ければ cookie の sid を失効させて cookie を消す")
  void logout_withoutBearerUsesCookie() {
    when(sessionTokenDecoder.sessionIdOf("refresh")).thenReturn(Optional.of("s-1"));

    assertThat(service.logout(null, "refresh")).isTrue();

    verify(tokenBlacklistService).blacklistSession("s-1");
  }

  @Test
  @DisplayName("ログアウト: 署名の通らない Bearer なら cookie へ切り替えず、どのセッションも止めない")
  void logout_brokenBearerDoesNotFallBackToCookie() {
    when(sessionTokenDecoder.sessionIdOf("broken")).thenReturn(Optional.empty());
    when(sessionTokenDecoder.sessionIdOf("refresh")).thenReturn(Optional.of("s-1"));

    assertThat(service.logout("Bearer broken", "refresh")).isFalse();

    verify(tokenBlacklistService, never()).blacklistSession(anyString());
  }

  @Test
  @DisplayName("資格情報変更イベント: イベントが運ぶ確定値をそのまま版キャッシュへ反映する")
  void onCredentialsChanged_reflectsCarriedVersion() {
    service.onCredentialsChanged(new UserCredentialsChanged(USER_ID, 3L));

    verify(credentialVersionService).reflect(USER_ID, 3L);
  }

  @Test
  @DisplayName("資格情報変更イベント: トランザクション内では commit 後まで反映を遅延する")
  void onCredentialsChanged_withinTransaction_defersUntilAfterCommit() {
    TransactionSynchronizationManager.initSynchronization();
    try {
      service.onCredentialsChanged(new UserCredentialsChanged(USER_ID, 3L));

      // commit 前に書くと「Redis 成功・commit 失敗」で増えていない版が残り、正当なトークンが誤拒否される
      verify(credentialVersionService, never()).reflect(anyLong(), anyLong());

      TransactionSynchronizationManager.getSynchronizations()
          .forEach(TransactionSynchronization::afterCommit);

      verify(credentialVersionService).reflect(USER_ID, 3L);
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  @DisplayName("資格情報変更イベント: rollback なら反映しない")
  void onCredentialsChanged_rollbackDoesNotReflect() {
    TransactionSynchronizationManager.initSynchronization();
    try {
      service.onCredentialsChanged(new UserCredentialsChanged(USER_ID, 3L));
      // rollback: afterCommit は発火しないまま同期が破棄される
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }

    verify(credentialVersionService, never()).reflect(anyLong(), anyLong());
  }
}
