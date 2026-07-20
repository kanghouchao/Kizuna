package com.kizuna.shared.storescope;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StoreContextTest {

  @Test
  @DisplayName("店舗 ID を設定すると hasStoreId が true になり取得できること")
  void setAndGetStoreId() {
    StoreContext context = new StoreContext();
    assertThat(context.hasStoreId()).isFalse();
    assertThat(context.getStoreId()).isNull();

    context.setStoreId(42L);

    assertThat(context.hasStoreId()).isTrue();
    assertThat(context.getStoreId()).isEqualTo(42L);
  }

  @Test
  @DisplayName("clear すると店舗文脈が消えること")
  void clear_removesStoreId() {
    StoreContext context = new StoreContext();
    context.setStoreId(42L);

    context.clear();

    assertThat(context.hasStoreId()).isFalse();
    assertThat(context.getStoreId()).isNull();
  }
}
