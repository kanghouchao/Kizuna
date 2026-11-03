package com.kizuna.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PagingTest {

  @Test
  @DisplayName("並びに一意キーが無ければ末尾に補い、位置は呼出側のまま運ぶこと")
  void appendsUniqueKeyAndKeepsWindow() {
    Pageable result =
        Paging.totalOrder(
            PageRequest.of(2, 30, Sort.by(BaseEntity::getUpdatedAt)),
            Sort.by(Sort.Order.desc(BaseEntity::getCreatedAt)),
            Sort.Order.desc(BaseEntity::getId));

    assertThat(result.getSort())
        .containsExactly(
            Sort.Order.desc(BaseEntity::getCreatedAt), Sort.Order.desc(BaseEntity::getId));
    assertThat(result.getPageNumber()).isEqualTo(2);
    assertThat(result.getPageSize()).isEqualTo(30);
  }

  @Test
  @DisplayName("一意キーが既に並びにあれば、向きも位置もそのままにすること")
  void keepsExistingUniqueKey() {
    Sort sort =
        Sort.by(Sort.Order.asc(BaseEntity::getUpdatedAt), Sort.Order.asc(BaseEntity::getId));

    Pageable result = Paging.totalOrder(0, 20, sort, Sort.Order.desc(BaseEntity::getId));

    assertThat(result.getSort()).isEqualTo(sort);
  }

  @Test
  @DisplayName("並びが空でも一意キーだけで全順序にすること")
  void unsortedBecomesUniqueKeyOnly() {
    Pageable result = Paging.totalOrder(0, 20, Sort.unsorted(), Sort.Order.asc(BaseEntity::getId));

    assertThat(result.getSort()).containsExactly(Sort.Order.asc(BaseEntity::getId));
  }
}
