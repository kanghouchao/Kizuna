package com.kizuna.shared.persistence;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * offset ページングの Pageable を組み立てる唯一の口。並びの末尾に一意キーを補い、同値行がページ境界を跨いで重複・欠落しないようにする。
 *
 * <p>呼出側が一意キーを向き付きで並びに含めていれば、その指定を変えない。
 */
public final class Paging {

  private Paging() {}

  public static Pageable totalOrder(Pageable window, Sort sort, Sort.Order uniqueKey) {
    return totalOrder(window.getPageNumber(), window.getPageSize(), sort, uniqueKey);
  }

  public static Pageable totalOrder(int page, int size, Sort sort, Sort.Order uniqueKey) {
    Sort total =
        sort.getOrderFor(uniqueKey.getProperty()) == null ? sort.and(Sort.by(uniqueKey)) : sort;
    return PageRequest.of(page, size, total);
  }
}
