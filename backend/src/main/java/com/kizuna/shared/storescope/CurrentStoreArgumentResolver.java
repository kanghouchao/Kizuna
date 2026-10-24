package com.kizuna.shared.storescope;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** {@link CurrentStore} 引数へ現在店舗の id を渡す。{@link StoreIdInterceptor} が検証して確立した店舗文脈を読むだけで、自らは検証しない。 */
@Component
@RequiredArgsConstructor
public class CurrentStoreArgumentResolver implements HandlerMethodArgumentResolver {

  private final StoreContext storeContext;

  @Override
  public boolean supportsParameter(@NonNull MethodParameter parameter) {
    return parameter.hasParameterAnnotation(CurrentStore.class)
        && Long.class.equals(parameter.getParameterType());
  }

  @Override
  public @Nullable Long resolveArgument(
      @NonNull MethodParameter parameter,
      @Nullable ModelAndViewContainer mavContainer,
      @NonNull NativeWebRequest webRequest,
      @Nullable WebDataBinderFactory binderFactory) {
    Long storeId = storeContext.getStoreId();
    if (storeId == null && parameter.getParameterAnnotation(CurrentStore.class).required()) {
      throw new AccessDeniedException("店舗文脈が解決できません");
    }
    return storeId;
  }
}
