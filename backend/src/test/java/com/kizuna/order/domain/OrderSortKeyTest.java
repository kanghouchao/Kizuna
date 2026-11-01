package com.kizuna.order.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 並び替えの鍵の定義の単体テスト。
 *
 * <p>ここで固定するのは式が満たすべき性質だけで、値の並びは統合テストが本物の PostgreSQL に当てて見る。
 */
class OrderSortKeyTest {

  /** 可空の列を鍵にする並び。未設定を最大として並べるため番兵へ寄せる。 */
  private static final OrderSortKey[] NULLABLE_KEYS = {
    OrderSortKey.ARRIVAL_TIME, OrderSortKey.PAX, OrderSortKey.COURSE_MINUTES
  };

  @Test
  @DisplayName("並び替えの式は受注自身の列だけを指すこと（join 先を鍵にしない）")
  void expression_referencesOnlyTheOrdersOwnColumns() {
    for (OrderSortKey key : OrderSortKey.values()) {
      // 顧客名・キャスト名を鍵にすると、並びが他の集約の書き換えで動く
      assertThat(key.expression()).as("%s の式", key).contains("o.");
      assertThat(key.expression()).as("%s の式", key).doesNotContain("c.").doesNotContain("k.");
    }
  }

  @Test
  @DisplayName("可空の列を鍵にする並びは、未設定を最大の番兵へ均すこと")
  void expression_coalescesUnsetKeysToTheSentinel() {
    for (OrderSortKey key : NULLABLE_KEYS) {
      // 均さないと `鍵 > :位置` が NULL 行に対して常に不成立になり、先頭ページ以降そこへ到達できない
      assertThat(key.expression()).as("%s の式", key).contains("coalesce");
      assertThat(key.expression()).as("%s の番兵", key).contains(String.valueOf(Integer.MAX_VALUE));
    }
  }
}
