package com.kizuna.review.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.review.domain.ReviewSearch.Order;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class ReviewSearchTest {

  private static Pageable pageable(Order order) {
    return new ReviewSearch(
            PageRequest.of(1, 10, Sort.by(ReviewRecord::getUpdatedAt)),
            null,
            null,
            null,
            null,
            order)
        .pageable();
  }

  @Test
  void descendingOrderEndsInDescendingId() {
    assertThat(pageable(Order.RECEIVED_DESC).getSort())
        .containsExactly(
            Sort.Order.desc(ReviewRecord::getReceivedAt), Sort.Order.desc(ReviewRecord::getId));
    assertThat(pageable(Order.RECEIVED_DESC).getPageNumber()).isEqualTo(1);
  }

  @Test
  void ascendingOrderEndsInAscendingId() {
    assertThat(pageable(Order.CREATED_ASC).getSort())
        .containsExactly(
            Sort.Order.asc(ReviewRecord::getCreatedAt), Sort.Order.asc(ReviewRecord::getId));
  }
}
