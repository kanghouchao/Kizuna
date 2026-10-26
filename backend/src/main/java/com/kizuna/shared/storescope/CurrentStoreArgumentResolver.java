package com.kizuna.shared.storescope;

import com.kizuna.shared.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.ThreadContext;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@code X-Store-ID} を検証して {@link CurrentStore} 引数へ店舗 id を渡す。認証済み主体は権限（PERM_）を持ち、かつ本人の店舗集合が
 * その店舗を含むときだけ名乗れる。本人の行に店舗を持つ CAST / MEMBER は作用域が null
 * に解決されて拒否されるが、権限の検査はそれに頼らず先に拒否する二重のガード。未認証（公開サイト）はヘッダだけで名乗れる。
 *
 * <p>拒否は例外送出で表明し、応答の成形は {@link com.kizuna.shared.exception.CommonExceptionHandler} へ委ねる（同じ status
 * の応答体を一つの形に保つため）。
 */
@Component
@RequiredArgsConstructor
public class CurrentStoreArgumentResolver implements HandlerMethodArgumentResolver {

  /** log4j2 のパターン（{@code store=%X{storeId:-}}）が読む MDC キー。 */
  public static final String MDC_STORE_ID = "storeId";

  private static final String HEADER_STORE_ID = "X-Store-ID";

  private final StoreScopeHolder storeScopeHolder;
  private final StoreExistenceCheck storeExistenceCheck;

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
    String header = webRequest.getHeader(HEADER_STORE_ID);
    if (StringUtils.isBlank(header)) {
      if (parameter.getParameterAnnotation(CurrentStore.class).required()) {
        throw new ServiceException("店舗を指定してください");
      }
      return null;
    }
    long storeId = parseStoreId(header);
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken) {
      if (!PermissionHolderGuard.holdsPermission(authentication)) {
        throw new AccessDeniedException("店舗を名乗る権限がありません");
      }
      StoreScope scope = storeScopeHolder.current();
      if (scope == null) {
        throw new AccessDeniedException("授権店舗集合を解決できません");
      }
      if (!scope.authorizes(storeId)) {
        throw new AccessDeniedException("この店舗の権限がありません");
      }
    }
    // 全店舗を授権された主体と未認証の公開サイトは実在しない id も名乗れ、外部キー任せでは 500 になるため 400 で拒否する。
    // 集合の検査より後に置くのは、特定店舗の主体に「実在しない」と「授権外」を区別させないため。
    if (!storeExistenceCheck.exists(storeId)) {
      throw new ServiceException("店舗が見つかりません");
    }
    ThreadContext.put(MDC_STORE_ID, String.valueOf(storeId));
    return storeId;
  }

  private static long parseStoreId(String header) {
    if (!StringUtils.isNumeric(header)) {
      throw new ServiceException("店舗 ID の形式が正しくありません");
    }
    try {
      return Long.parseLong(header);
    } catch (NumberFormatException e) {
      throw new ServiceException("店舗 ID の形式が正しくありません");
    }
  }
}
