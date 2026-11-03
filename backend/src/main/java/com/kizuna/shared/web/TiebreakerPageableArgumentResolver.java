package com.kizuna.shared.web;

import com.kizuna.shared.persistence.Paging;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableArgumentResolver;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 一覧 API の {@link Pageable} の並びへ、束縛の時点で一意な副キーを補う。
 *
 * <p>重複しうる列だけの並びは offset ページングで行の重複・取りこぼしを招く。{@code ?sort=} は {@code @PageableDefault}
 * の既定を丸ごと置き換えるので、端点ごとに書く対策では漏れる。
 */
public class TiebreakerPageableArgumentResolver implements PageableArgumentResolver {

  /** 全 aggregate が一意な id を持つため、副キーは id に固定する。 */
  private static final Sort.Order TIEBREAKER = Sort.Order.asc("id");

  private final PageableArgumentResolver delegate;

  public TiebreakerPageableArgumentResolver(PageableArgumentResolver delegate) {
    this.delegate = delegate;
  }

  @Override
  public boolean supportsParameter(@NonNull MethodParameter parameter) {
    return delegate.supportsParameter(parameter);
  }

  @Override
  public @NonNull Pageable resolveArgument(
      @NonNull MethodParameter parameter,
      @Nullable ModelAndViewContainer mavContainer,
      @NonNull NativeWebRequest webRequest,
      @Nullable WebDataBinderFactory binderFactory) {
    Pageable pageable =
        delegate.resolveArgument(parameter, mavContainer, webRequest, binderFactory);
    return Paging.totalOrder(pageable, TIEBREAKER);
  }
}
