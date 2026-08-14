package com.kizuna.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 並び替えの鍵が「続きの位置」を作る規則の単体テスト。
 *
 * <p>ここが問い合わせ側の式（{@link OrderSortKey#expression()}）とずれると、続きが手前へ戻るか行を飛ばす。
 * 特に未設定を番兵へ均す部分は、素の列で比較する実装との違いがそのままカーソルの到達性になる。
 */
class OrderSortKeyTest {

  /** 未設定の鍵が均される先。問い合わせ側の式が使う整数リテラルと同じ値。 */
  private static final String SENTINEL = String.valueOf(Integer.MAX_VALUE);

  private OrderView viewWith(
      LocalDate businessDate, LocalTime arrival, Integer pax, Integer course) {
    OrderView view = mock(OrderView.class);
    when(view.getBusinessDate()).thenReturn(businessDate);
    when(view.getArrivalScheduledStartTime()).thenReturn(arrival);
    when(view.getPax()).thenReturn(pax);
    when(view.getCourseMinutes()).thenReturn(course);
    return view;
  }

  @Test
  @DisplayName("値を持つ行の鍵は、問い合わせ側の式と同じ値になること")
  void cursorValueOf_withValues() {
    OrderView view = viewWith(LocalDate.parse("2026-08-15"), LocalTime.of(19, 30), 2, 90);

    assertThat(OrderSortKey.BUSINESS_DATE.cursorValueOf(view)).isEqualTo("2026-08-15");
    // 時刻は「その日の何分目か」に均す（番兵に整数リテラルだけを使うため）
    assertThat(OrderSortKey.ARRIVAL_TIME.cursorValueOf(view)).isEqualTo("1170");
    assertThat(OrderSortKey.PAX.cursorValueOf(view)).isEqualTo("2");
    assertThat(OrderSortKey.COURSE_MINUTES.cursorValueOf(view)).isEqualTo("90");
  }

  @Test
  @DisplayName("未設定の鍵は最大の番兵へ均されること（均さないとその行へ二度と到達できない）")
  void cursorValueOf_unsetKeysFallBackToTheSentinel() {
    OrderView view = viewWith(LocalDate.parse("2026-08-15"), null, null, null);

    assertThat(OrderSortKey.ARRIVAL_TIME.cursorValueOf(view)).isEqualTo(SENTINEL);
    assertThat(OrderSortKey.PAX.cursorValueOf(view)).isEqualTo(SENTINEL);
    assertThat(OrderSortKey.COURSE_MINUTES.cursorValueOf(view)).isEqualTo(SENTINEL);
  }

  @Test
  @DisplayName("鍵の型は復号の分岐と対応すること")
  void keyType_matchesTheDecodingBranch() {
    assertThat(OrderSortKey.BUSINESS_DATE.keyType()).isEqualTo(OrderSortKey.KeyType.DATE);
    assertThat(OrderSortKey.ARRIVAL_TIME.keyType()).isEqualTo(OrderSortKey.KeyType.NUMBER);
    assertThat(OrderSortKey.PAX.keyType()).isEqualTo(OrderSortKey.KeyType.NUMBER);
    assertThat(OrderSortKey.COURSE_MINUTES.keyType()).isEqualTo(OrderSortKey.KeyType.NUMBER);
  }

  @Test
  @DisplayName("並び替えの式は受注自身の列だけを指すこと（join 先を鍵にしない）")
  void expression_referencesOnlyTheOrdersOwnColumns() {
    for (OrderSortKey key : OrderSortKey.values()) {
      // 顧客名・キャスト名を鍵にすると、並びが他の集約の書き換えで動く
      assertThat(key.expression()).as("%s の式", key).contains("o.");
      assertThat(key.expression()).as("%s の式", key).doesNotContain("c.").doesNotContain("k.");
    }
  }
}
