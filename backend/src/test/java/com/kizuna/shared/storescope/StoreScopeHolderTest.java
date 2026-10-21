package com.kizuna.shared.storescope;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StoreScopeHolderTest {

  private final StoreScopeHolder holder = new StoreScopeHolder();

  @AfterEach
  void clear() {
    holder.clear();
  }

  @Test
  @DisplayName("未設定なら null（fail-closed の入力）")
  void currentIsNullWhenNothingInstalled() {
    assertThat(holder.current()).isNull();
  }

  @Test
  @DisplayName("解決器は最初の current() まで呼ばれず、以後は記憶した値を返す（1 回だけ解決する）")
  void resolvesLazilyAndOnce() {
    AtomicInteger calls = new AtomicInteger();
    StoreScope scope = new StoreScope(false, Set.of(1L));
    holder.install(
        () -> {
          calls.incrementAndGet();
          return scope;
        });

    assertThat(calls).hasValue(0);
    assertThat(holder.current()).isSameAs(scope);
    assertThat(holder.current()).isSameAs(scope);
    assertThat(calls).hasValue(1);
  }

  @Test
  @DisplayName("解決器が null を返す場合も 1 回だけ解決し、以後は null を返す")
  void memoizesNullResolution() {
    AtomicInteger calls = new AtomicInteger();
    holder.install(
        () -> {
          calls.incrementAndGet();
          return null;
        });

    assertThat(holder.current()).isNull();
    assertThat(holder.current()).isNull();
    assertThat(calls).hasValue(1);
  }

  @Test
  @DisplayName("clear 後は null、差し替えると記憶も捨てる")
  void clearAndReinstallDropMemo() {
    holder.install(() -> new StoreScope(true, Set.of()));
    assertThat(holder.current().allStores()).isTrue();

    holder.clear();
    assertThat(holder.current()).isNull();

    holder.install(() -> new StoreScope(false, Set.of(2L)));
    assertThat(holder.current().storeIds()).containsExactly(2L);
  }

  @Test
  @DisplayName("保持はスレッド局所で、別スレッドには見えない")
  void isThreadLocal() throws InterruptedException {
    holder.install(() -> new StoreScope(true, Set.of()));
    StoreScope[] seen = new StoreScope[1];
    Thread other = new Thread(() -> seen[0] = holder.current());
    other.start();
    other.join();

    assertThat(seen[0]).isNull();
    assertThat(holder.current()).isNotNull();
  }
}
