package com.kizuna.shared.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * offset ページングの一覧 API 向けユーティリティ。
 *
 * <p>{@code @PageableDefault(sort = {...})} は呼出側が {@code ?sort=} を送ると既定値を丸ごと置き換えるため、
 * 一意な副キーとして添えた並びが消える。表示名などの並びが重複しうる列だとページ境界を跨いだ相対順序が未定義になり、 offset
 * ページングで行の重複・取りこぼしを招くため、呼出側の並びに副キーが無ければ補う。
 */
public final class PageableSupport {

  private PageableSupport() {}

  /** {@code pageable} の並びに {@code tiebreakerProperty} が無ければ末尾に補って返す。既にあれば変更しない。 */
  public static Pageable ensureTiebreaker(Pageable pageable, String tiebreakerProperty) {
    Sort sort = pageable.getSort();
    if (sort.getOrderFor(tiebreakerProperty) != null) {
      return pageable;
    }
    return PageRequest.of(
        pageable.getPageNumber(), pageable.getPageSize(), sort.and(Sort.by(tiebreakerProperty)));
  }
}
