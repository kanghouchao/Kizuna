package com.kizuna.point.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 「受注を根拠とするすべての付与が追跡・取消できる」のうち、行内では表せない側の守衛。
 *
 * <p>受注を根拠とする加算が受注 ID を持つことは行級 DB CHECK が押さえるが、<b>どの種別が加算になりうるか</b>は行からは見えない。
 * 加算になりうる種別を足した者にここを更新させ、そのとき巻き戻しの収集述語（受注 ID を持つ加算行）で拾えるかを 判断させるための枚挙である。
 */
class PointEntryTypeTest {

  /**
   * 加算になりうる種別と、それが受注を根拠とする付与かどうか。
   *
   * <p>受注を根拠とするものは受注 ID を持たなければならない — 持たなければ、受注から辿る巻き戻しがその行を永久に
   * 見つけられない。利用取消は受注の授受を打ち消す側なので付与ではなく、受注 ID を持たない。
   */
  private static final Set<PointEntryType> ORDER_BASED_CREDITS = Set.of(PointEntryType.ORDER_GRANT);

  private static final Set<PointEntryType> OTHER_CREDITS =
      Set.of(PointEntryType.MANUAL_ADJUST, PointEntryType.USE_CANCEL);

  @Test
  @DisplayName("加算になりうる種別は枚挙どおりであること（増やしたら巻き戻しの収集述語を見直す）")
  void creditableTypesAreExactlyTheEnumeratedOnes() {
    Set<PointEntryType> enumerated =
        Set.copyOf(Stream.concat(ORDER_BASED_CREDITS.stream(), OTHER_CREDITS.stream()).toList());

    List<PointEntryType> creditable =
        Arrays.stream(PointEntryType.values()).filter(PointEntryType::creditable).toList();

    assertThat(creditable).containsExactlyInAnyOrderElementsOf(enumerated);
  }

  @Test
  @DisplayName("受注を根拠とする加算だけが受注 ID を持ち、巻き戻しの収集述語で拾えること")
  void onlyOrderBasedCreditsCarryTheOrderId() {
    assertThat(ORDER_BASED_CREDITS).as("枚挙が空だと以下の断言が空振りになる").isNotEmpty();

    assertThat(PointEntry.grantForOrder(7L, "o1", 3L, 500, 9L).getOrderId())
        .as("受注付与は受注 ID を持つ")
        .isEqualTo("o1");
    assertThat(
            PointEntry.manualAdjust(7L, 3L, 500, "理由", null, List.of(), 9L, "key-1").getOrderId())
        .as("手動調整は受注を根拠としない")
        .isNull();
    assertThat(PointEntry.reverseUse(persistedUse(), "巻き戻し", 9L).getOrderId())
        .as("利用取消は打ち消す側であって付与ではない")
        .isNull();
  }

  @Test
  @DisplayName("利用取消は加算だが新しいロットにならないこと（期限を持たない）")
  void useCancelIsACreditThatNeverBecomesALot() {
    PointEntry reversed = PointEntry.reverseUse(persistedUse(), "巻き戻し", 9L);

    assertThat(reversed.getAmount()).isPositive();
    assertThat(reversed.getExpiresOn()).isNull();
    assertThat(reversed.getAllocations())
        .as("元の利用が引いたロットへ同量を返す")
        .extracting(PointAllocation::getSourceEntryId, PointAllocation::getAmount)
        .containsExactly(tuple(11L, 300));
  }

  private static PointEntry persistedUse() {
    PointEntry use =
        PointEntry.useForOrder(7L, "o1", 3L, 300, List.of(PointAllocation.of(11L, 300)), 9L);
    use.setId(21L);
    return use;
  }
}
