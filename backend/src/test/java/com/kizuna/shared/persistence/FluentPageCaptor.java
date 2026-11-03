package com.kizuna.shared.persistence;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.function.Function;
import org.mockito.stubbing.Answer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor.SpecificationFluentQuery;

/** {@code findBy(spec, q -> q.as(..).page(pageable))} の形の取得で、リポジトリへ渡る Pageable を捕まえる。 */
public final class FluentPageCaptor {

  private Pageable captured;

  @SuppressWarnings({"unchecked", "rawtypes"})
  public Answer<Object> answer() {
    return invocation -> {
      SpecificationFluentQuery query = mock(SpecificationFluentQuery.class, RETURNS_SELF);
      when(query.page(any(Pageable.class)))
          .thenAnswer(
              page -> {
                captured = page.getArgument(0);
                return Page.empty();
              });
      return ((Function) invocation.getArgument(1)).apply(query);
    };
  }

  public Pageable captured() {
    return captured;
  }
}
