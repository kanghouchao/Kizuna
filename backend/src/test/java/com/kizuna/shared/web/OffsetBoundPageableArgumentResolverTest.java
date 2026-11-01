package com.kizuna.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.shared.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableArgumentResolver;

class OffsetBoundPageableArgumentResolverTest {

  private final PageableArgumentResolver delegate = mock(PageableArgumentResolver.class);
  private final OffsetBoundPageableArgumentResolver resolver =
      new OffsetBoundPageableArgumentResolver(delegate);

  private Pageable resolve(Pageable resolvedByDelegate) {
    when(delegate.resolveArgument(any(), any(), any(), any())).thenReturn(resolvedByDelegate);
    return resolver.resolveArgument(null, null, null, null);
  }

  @Test
  @DisplayName("取得位置が int の上限ちょうどまでならそのまま通すこと")
  void passesOffsetUpToIntMax() {
    Pageable pageable = PageRequest.of(Integer.MAX_VALUE, 1);

    assertThat(resolve(pageable)).isSameAs(pageable);
  }

  @Test
  @DisplayName("取得位置が int を超えるページ指定は ServiceException（400）にすること")
  void rejectsOffsetBeyondIntMax() {
    assertThatThrownBy(() -> resolve(PageRequest.of(99_999_999, 100)))
        .isInstanceOf(ServiceException.class);
  }

  @Test
  @DisplayName("ページ指定の無い Pageable は検査せずに通すこと")
  void passesUnpaged() {
    assertThat(resolve(Pageable.unpaged())).isEqualTo(Pageable.unpaged());
  }
}
