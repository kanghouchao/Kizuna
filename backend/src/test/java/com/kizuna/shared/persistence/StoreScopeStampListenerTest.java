package com.kizuna.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** {@link StoreScopeStampListener} は store_id を補わず、未設定だけを拒否する。 */
class StoreScopeStampListenerTest {

  private final StoreScopeStampListener listener = new StoreScopeStampListener();

  /** テスト用の具象 store-scoped エンティティ（JPA 不使用の POJO）。 */
  private static final class TestEntity extends StoreScopedEntity {}

  @Test
  void presetStoreIdPassesUntouched() {
    TestEntity entity = new TestEntity();
    entity.setStoreId(5L);

    listener.requireStoreId(entity);

    assertThat(entity.getStoreId()).isEqualTo(5L);
  }

  @Test
  void rejectsMissingStoreIdNamingTheEntity() {
    TestEntity entity = new TestEntity();

    assertThatThrownBy(() -> listener.requireStoreId(entity))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(TestEntity.class.getName());
    assertThat(entity.getStoreId()).isNull();
  }
}
