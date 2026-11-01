package com.kizuna.shared.web;

import com.kizuna.shared.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableArgumentResolver;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 解決した {@link Pageable} の取得位置（page × size）が int に収まらなければ 400 にする解決器。
 *
 * <p>Spring Data は page の上限を設けず、取得位置を long で持つが、JPA の開始位置は int のため、 範囲外のページ番号がそのまま問い合わせに届くと 500
 * になる。束縛の時点で弾くことで全一覧に同じ境界を敷く。
 */
@RequiredArgsConstructor
public class OffsetBoundPageableArgumentResolver implements PageableArgumentResolver {

  private final PageableArgumentResolver delegate;

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
    if (pageable.isPaged() && pageable.getOffset() > Integer.MAX_VALUE) {
      throw new ServiceException("ページの指定を確認してください");
    }
    return pageable;
  }
}
