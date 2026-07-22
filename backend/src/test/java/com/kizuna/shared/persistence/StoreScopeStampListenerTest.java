package com.kizuna.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.shared.storescope.StoreContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * {@link StoreScopeStampListener} の @PrePersist 採番挙動を固定する。実 {@link StoreContext}（ThreadLocal）を用い、
 * 設定済み尊重・文脈からの採番・文脈欠如での fail-loud の三分岐を検証する。
 */
class StoreScopeStampListenerTest {

  private final StoreContext storeContext = new StoreContext();
  private final StoreScopeStampListener listener = new StoreScopeStampListener(storeContext);

  /** テスト用の具象 store-scoped エンティティ（JPA 不使用の POJO）。 */
  private static final class TestEntity extends StoreScopedEntity {}

  @AfterEach
  void clearContext() {
    storeContext.clear();
  }

  @Test
  void preSetStoreIdIsRespected() {
    storeContext.setStoreId(99L);
    TestEntity entity = new TestEntity();
    entity.setStoreId(5L);

    listener.stampStoreId(entity);

    assertThat(entity.getStoreId()).as("設定済みの値は文脈より優先される").isEqualTo(5L);
  }

  @Test
  void stampsFromContextWhenUnset() {
    storeContext.setStoreId(42L);
    TestEntity entity = new TestEntity();

    listener.stampStoreId(entity);

    assertThat(entity.getStoreId()).isEqualTo(42L);
  }

  @Test
  void failsLoudWhenContextMissing() {
    TestEntity entity = new TestEntity();

    assertThatThrownBy(() -> listener.stampStoreId(entity))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("店舗文脈");
  }
}
