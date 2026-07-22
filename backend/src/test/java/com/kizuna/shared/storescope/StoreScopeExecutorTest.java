package com.kizuna.shared.storescope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import io.jsonwebtoken.Claims;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * {@link StoreScopeExecutor} の授権検査・文脈 set→action→clear ライフサイクル（例外時の clear 含む）を実 {@link
 * StoreContext} で固定する。旧 PlatformOrderServiceTest が実 StoreContext で担っていた検証を本 seam のテストへ移した。
 */
@ExtendWith(MockitoExtension.class)
class StoreScopeExecutorTest {

  private final StoreContext storeContext = new StoreContext();
  private final StoreScopeExecutor executor = new StoreScopeExecutor(storeContext);

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
    storeContext.clear();
  }

  private void authenticate(String scopeType, Object storeIds) {
    Claims claims = mock(Claims.class);
    lenient().when(claims.get("storeScopeType", String.class)).thenReturn(scopeType);
    lenient().when(claims.get("storeIds")).thenReturn(storeIds);
    Authentication auth = mock(Authentication.class);
    lenient().when(auth.getDetails()).thenReturn(claims);
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @Test
  void failsClosedWhenScopeUnresolved() {
    // 認証コンテキスト未設定 → StoreScope 解決不能
    AtomicBoolean ran = new AtomicBoolean(false);

    assertThatThrownBy(
            () ->
                executor.runInStore(
                    1L,
                    () -> {
                      ran.set(true);
                      return "x";
                    }))
        .isInstanceOf(AccessDeniedException.class);

    assertThat(ran).as("action は実行されないこと").isFalse();
    assertThat(storeContext.getStoreId()).isNull();
  }

  @Test
  void rejectsOutOfSetStore() {
    authenticate("SPECIFIC_STORES", List.of(1));
    AtomicBoolean ran = new AtomicBoolean(false);

    assertThatThrownBy(
            () ->
                executor.runInStore(
                    2L,
                    () -> {
                      ran.set(true);
                      return "x";
                    }))
        .isInstanceOf(AccessDeniedException.class);

    assertThat(ran).isFalse();
    assertThat(storeContext.getStoreId()).isNull();
  }

  @Test
  void runsActionUnderStoreScopeAndClearsAfter() {
    authenticate("SPECIFIC_STORES", List.of(1));
    AtomicReference<Long> storeAtCall = new AtomicReference<>();

    String result =
        executor.runInStore(
            1L,
            () -> {
              storeAtCall.set(storeContext.getStoreId());
              return "ok";
            });

    assertThat(result).isEqualTo("ok");
    assertThat(storeAtCall.get()).as("action 実行時点で StoreContext が storeId").isEqualTo(1L);
    assertThat(storeContext.getStoreId()).as("復帰後は finally で clear 済み").isNull();
  }

  @Test
  void allowsAnyStoreForAllStoresScope() {
    authenticate("ALL_STORES", null);
    AtomicReference<Long> storeAtCall = new AtomicReference<>();

    executor.runInStore(
        999L,
        () -> {
          storeAtCall.set(storeContext.getStoreId());
          return "ok";
        });

    assertThat(storeAtCall.get()).isEqualTo(999L);
    assertThat(storeContext.getStoreId()).isNull();
  }

  @Test
  void clearsContextEvenWhenActionThrows() {
    authenticate("SPECIFIC_STORES", List.of(1));

    assertThatThrownBy(
            () ->
                executor.runInStore(
                    1L,
                    () -> {
                      throw new IllegalStateException("boom");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(storeContext.getStoreId()).as("例外時も finally で clear される").isNull();
  }
}
