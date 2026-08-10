package com.kizuna.store.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StoreTest {

  @Test
  @DisplayName("新規登録した店舗は準備中から始まること")
  void newStore_startsPreparing() {
    Store store = new Store("新規店舗", "new.kizuna.test", null);

    assertThat(store.getStatus()).isEqualTo(StoreStatus.PREPARING);
  }

  @Test
  @DisplayName("準備中の店舗を稼働中へ遷移できること")
  void activate_fromPreparing() {
    Store store = new Store("準備中店舗", "preparing.kizuna.test", null);

    store.activate();

    assertThat(store.getStatus()).isEqualTo(StoreStatus.ACTIVE);
  }

  // 遷移の引き金は店舗コンソールへの着地で、同じ利用者が訪れるたびに呼ばれる。
  @Test
  @DisplayName("稼働中の店舗を再度稼働させても状態が変わらないこと")
  void activate_whenAlreadyActive_isIdempotent() {
    Store store = new Store("稼働中店舗", "active.kizuna.test", null);
    store.activate();

    store.activate();

    assertThat(store.getStatus()).isEqualTo(StoreStatus.ACTIVE);
  }
}
