package com.kizuna.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableArgumentResolver;

/** {@link TiebreakerPageableArgumentResolver} の副キー補完の単体テスト。 */
class TiebreakerPageableArgumentResolverTest {

  private final PageableArgumentResolver delegate = mock(PageableArgumentResolver.class);
  private final TiebreakerPageableArgumentResolver resolver =
      new TiebreakerPageableArgumentResolver(delegate);

  private Pageable resolve(Pageable resolvedByDelegate) {
    when(delegate.resolveArgument(any(), any(), any(), any())).thenReturn(resolvedByDelegate);
    return resolver.resolveArgument(null, null, null, null);
  }

  @Test
  @DisplayName("呼出側の並びに副キーが無ければ末尾に補うこと")
  void appendsTiebreakerWhenMissing() {
    Pageable result = resolve(PageRequest.of(0, 20, Sort.by("displayName")));

    assertThat(result.getSort()).containsExactly(Sort.Order.by("displayName"), Sort.Order.by("id"));
    assertThat(result.getPageNumber()).isEqualTo(0);
    assertThat(result.getPageSize()).isEqualTo(20);
  }

  @Test
  @DisplayName("副キーが既に含まれていれば並びを変えないこと")
  void leavesSortUnchangedWhenTiebreakerAlreadyPresent() {
    Sort sort = Sort.by("createdAt").descending().and(Sort.by("id"));

    Pageable result = resolve(PageRequest.of(1, 10, sort));

    assertThat(result.getSort()).isEqualTo(sort);
  }

  @Test
  @DisplayName("副キーを明示的に降順で指定していれば、その向きのまま変えないこと")
  void leavesExplicitTiebreakerDirectionUnchanged() {
    Sort sort = Sort.by(Sort.Order.desc("id"));

    Pageable result = resolve(PageRequest.of(0, 20, sort));

    assertThat(result.getSort()).isEqualTo(sort);
  }
}
