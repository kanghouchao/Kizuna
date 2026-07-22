package com.kizuna.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.auth.infrastructure.TokenBlacklistService;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.PlatformUserResumed;
import com.kizuna.user.domain.PlatformUserStopped;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class AuthSessionServiceTest {

  private static final String EMAIL = "target@kizuna.test";

  @Mock private TokenBlacklistService tokenBlacklistService;

  @Mock private PlatformUserRepository platformUserRepository;

  @Mock private PlatformTransactionManager transactionManager;

  private AuthSessionService service;

  @BeforeEach
  void setUp() {
    // TransactionTemplate は実物を使う（コールバックを実際に走らせる）。確定済みの行を独立
    // トランザクションで読む設計のため、ここを模擬すると読み直しの有無を検証できなくなる。
    // lenient: invalidate 系のテストは確定状態の読み直しを通らないため、この stub は使われない。
    lenient()
        .when(transactionManager.getTransaction(any()))
        .thenReturn(mock(TransactionStatus.class));
    service =
        new AuthSessionService(tokenBlacklistService, platformUserRepository, transactionManager);
  }

  /** コミット済みの enabled を返すユーザーを仕込む。 */
  private void committedUser(boolean enabled) {
    PlatformUser user =
        PlatformUser.builder()
            .email(EMAIL)
            .password("x")
            .displayName("対象")
            .enabled(enabled)
            .userType(UserType.CAST)
            .storeScopeType(StoreScopeType.ALL_STORES)
            .storeIds(Set.of())
            .build();
    when(platformUserRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
  }

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
  @DisplayName("停止イベント: コミット済みが停止済みならユーザー単位ブラックリストへ登録する")
  void onStopped_whenCommittedStateIsStopped_blacklists() {
    committedUser(false);

    service.onPlatformUserStopped(new PlatformUserStopped(EMAIL));

    verify(tokenBlacklistService).blacklistUser(EMAIL);
  }

  @Test
  @DisplayName("停止イベント: コミット済みが有効なら登録しない（並行再開に負けた停止要求が無関係なユーザーを締め出さない）")
  void onStopped_whenCommittedStateIsEnabled_doesNotBlacklist() {
    committedUser(true);

    service.onPlatformUserStopped(new PlatformUserStopped(EMAIL));

    verify(tokenBlacklistService, never()).blacklistUser(anyString());
  }

  @Test
  @DisplayName("再開イベント: コミット済みが有効ならユーザー単位ブラックリストを解除する")
  void onResumed_whenCommittedStateIsEnabled_clears() {
    committedUser(true);

    service.onPlatformUserResumed(new PlatformUserResumed(EMAIL));

    verify(tokenBlacklistService).clearUser(EMAIL);
  }

  @Test
  @DisplayName("再開イベント: コミット済みが停止済みなら解除しない（停止済みユーザーの旧 JWT を復活させない — PR #435 codex 指摘）")
  void onResumed_whenCommittedStateIsStopped_doesNotClear() {
    committedUser(false);

    service.onPlatformUserResumed(new PlatformUserResumed(EMAIL));

    verify(tokenBlacklistService, never()).clearUser(anyString());
  }

  @Test
  @DisplayName("対象ユーザーが不在なら停止側へ倒す（登録はするが解除はしない）")
  void missingUser_failsClosed() {
    when(platformUserRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

    service.onPlatformUserStopped(new PlatformUserStopped(EMAIL));
    service.onPlatformUserResumed(new PlatformUserResumed(EMAIL));

    verify(tokenBlacklistService).blacklistUser(EMAIL);
    verify(tokenBlacklistService, never()).clearUser(anyString());
  }
}
