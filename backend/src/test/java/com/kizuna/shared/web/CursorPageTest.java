package com.kizuna.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CursorPageTest {

  @Test
  @DisplayName("上限より 1 件多い結果は、余分を返さず続きの位置に変えること")
  void turnsTheExtraRowIntoTheNextCursor() {
    CursorPage<String> page = CursorPage.of(List.of("a", "b", "c"), 2, row -> "cursor-" + row);

    assertThat(page.content()).containsExactly("a", "b");
    // 位置は返した最後の行を指す。余分に取った行を指すと、その行が飛ばされる。
    assertThat(page.nextCursor()).isEqualTo("cursor-b");
  }

  @Test
  @DisplayName("上限に満たない結果には続きの位置を付けないこと")
  void reportsNoCursorWhenNothingFollows() {
    CursorPage<String> page = CursorPage.of(List.of("a"), 2, row -> "cursor-" + row);

    assertThat(page.content()).containsExactly("a");
    assertThat(page.nextCursor()).isNull();
  }

  @Test
  @DisplayName("ちょうど上限だけの結果にも続きの位置を付けないこと")
  void reportsNoCursorWhenTheResultExactlyFillsTheLimit() {
    // 上限ちょうどで続きありと答えると、空の続きを 1 回余計に取りに行かせる。
    assertThat(CursorPage.of(List.of("a", "b"), 2, row -> "cursor-" + row).nextCursor()).isNull();
  }

  @Test
  @DisplayName("写しても続きの位置を持ち越すこと")
  void keepsTheCursorWhenMapped() {
    CursorPage<String> mapped =
        CursorPage.of(List.of(1, 2, 3), 2, row -> "cursor-" + row).map(String::valueOf);

    assertThat(mapped.content()).containsExactly("1", "2");
    assertThat(mapped.nextCursor()).isEqualTo("cursor-2");
  }

  @Test
  @DisplayName("要求された取得件数を許容範囲に収めること")
  void clampsTheRequestedSize() {
    assertThat(CursorPage.clampSize(10_000)).isEqualTo(CursorPage.MAX_SIZE);
    assertThat(CursorPage.clampSize(0)).isEqualTo(1);
    assertThat(CursorPage.clampSize(20)).isEqualTo(20);
  }
}
