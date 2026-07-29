package com.kizuna.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** {@link PageableSupport} の副キー補完の単体テスト。 */
class PageableSupportTest {

  @Test
  @DisplayName("呼出側の並びに副キーが無ければ末尾に補うこと")
  void appendsTiebreakerWhenMissing() {
    Pageable pageable = PageRequest.of(0, 20, Sort.by("displayName"));

    Pageable result = PageableSupport.ensureTiebreaker(pageable, "id");

    assertThat(result.getSort()).containsExactly(Sort.Order.by("displayName"), Sort.Order.by("id"));
    assertThat(result.getPageNumber()).isEqualTo(0);
    assertThat(result.getPageSize()).isEqualTo(20);
  }

  @Test
  @DisplayName("副キーが既に含まれていれば並びを変えないこと")
  void leavesSortUnchangedWhenTiebreakerAlreadyPresent() {
    Sort sort = Sort.by("createdAt").descending().and(Sort.by("id"));
    Pageable pageable = PageRequest.of(1, 10, sort);

    Pageable result = PageableSupport.ensureTiebreaker(pageable, "id");

    assertThat(result.getSort()).isEqualTo(sort);
  }

  @Test
  @DisplayName("副キーを明示的に降順で指定していれば、その向きのまま変えないこと")
  void leavesExplicitTiebreakerDirectionUnchanged() {
    Sort sort = Sort.by(Sort.Order.desc("id"));
    Pageable pageable = PageRequest.of(0, 20, sort);

    Pageable result = PageableSupport.ensureTiebreaker(pageable, "id");

    assertThat(result.getSort()).isEqualTo(sort);
  }
}
