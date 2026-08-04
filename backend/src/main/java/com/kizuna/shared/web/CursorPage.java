package com.kizuna.shared.web;

import java.util.List;
import java.util.function.Function;

/**
 * カーソルページングの応答外殻。総件数を持たないのは、続きの有無だけが分かれば到達性は保てるためで、 一覧を取るたびに件数の問い合わせを撒かない。
 *
 * @param content この取得で返す行
 * @param nextCursor 続きの取得に渡す位置。続きが無いときは null（応答からも省かれる）
 */
public record CursorPage<T>(List<T> content, String nextCursor) {

  /**
   * 1 回の取得件数の上限。{@code spring.data.web.pageable.max-page-size} の既定値と同じ数を採るのは、Pageable
   * を離れる読み口だけが上限を失って青天井にならないようにするため — 呼出側から見える天井は据え置きになる。
   *
   * <p>ここで抑えても続きはカーソルで辿れるので、到達性はこの値に依存しない。
   */
  public static final int MAX_SIZE = 2000;

  /** 要求された取得件数を許容範囲に収める。 */
  public static int clampSize(int requested) {
    return Math.clamp(requested, 1, MAX_SIZE);
  }

  /**
   * 上限より 1 件多く取った結果から応答を組む。余分の 1 件は「続きがある」ことの証拠にだけ使い、返さない。
   *
   * @param fetched {@code size + 1} 件を上限に取得した行（並びは読み口が確定させたもの）
   * @param size 呼出側へ返す件数の上限
   * @param cursorOf 行から続きの位置を作る写像
   */
  public static <T> CursorPage<T> of(List<T> fetched, int size, Function<T, String> cursorOf) {
    if (fetched.size() <= size) {
      return new CursorPage<>(List.copyOf(fetched), null);
    }
    List<T> content = List.copyOf(fetched.subList(0, size));
    return new CursorPage<>(content, cursorOf.apply(content.get(size - 1)));
  }

  /** 読み側 projection の外殻を、応答 DTO の外殻へ写す。続きの位置は projection から作った値をそのまま持ち越す。 */
  public <R> CursorPage<R> map(Function<T, R> mapper) {
    return new CursorPage<>(content.stream().map(mapper).toList(), nextCursor);
  }
}
