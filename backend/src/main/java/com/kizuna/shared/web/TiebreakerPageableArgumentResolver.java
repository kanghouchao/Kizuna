package com.kizuna.shared.web;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableArgumentResolver;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 一覧 API の {@link Pageable} を解決した直後に、並びへ一意な副キーを補う解決器。
 *
 * <p>表示名などの重複しうる列だけで並べると、ページ境界を跨いだ相対順序が未定義になり、offset ページングで行の重複・取りこぼしを招く。 {@code @PageableDefault}
 * の既定値は呼出側の {@code ?sort=} で丸ごと置き換わるため、既定に副キーを書くだけでは足りない。
 *
 * <p>補完を controller 側の呼出に委ねると、呼び忘れた端点だけがこの問題を起こす。束縛の時点で補うことで、一覧を追加する側が 全順序という不変量を知らなくても壊せない。
 */
public class TiebreakerPageableArgumentResolver implements PageableArgumentResolver {

  /** 全 aggregate が一意な id を持つため、副キーは id に固定する。 */
  private static final String TIEBREAKER_PROPERTY = "id";

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
    Sort sort = pageable.getSort();
    // 呼出側が向きまで指定している場合はその指定を尊重する。
    if (sort.getOrderFor(TIEBREAKER_PROPERTY) != null) {
      return pageable;
    }
    return PageRequest.of(
        pageable.getPageNumber(), pageable.getPageSize(), sort.and(Sort.by(TIEBREAKER_PROPERTY)));
  }
}
