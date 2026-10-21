package com.kizuna.shared.storescope;

import com.kizuna.shared.exception.ServiceException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * {@code X-Store-ID} から店舗文脈を確立する。認証済み主体は権限（PERM_）を 1 つ以上持ち、かつ要求ごとに解決した本人の店舗集合が
 * その店舗を含むときだけ名乗れる（fail-closed）。権限の検査を先に置くのは、招待を受けた CAST は本人の店舗集合に店舗を持つため
 * 集合の検査だけでは店舗文脈を名乗れてしまうから。未認証（公開サイト）はヘッダだけで店舗を名乗れる。
 *
 * <p>拒否は例外送出で表明し、応答の成形は {@link com.kizuna.shared.exception.CommonExceptionHandler} へ委ねる — ここでは
 * response へ直接書かない。同じ status を返す経路が複数のワイヤ形を持つと、呼出側が応答体の形を一つに決められなくなるため。
 */
@Log4j2
@Component
@AllArgsConstructor
public class StoreIdInterceptor implements HandlerInterceptor {

  private final StoreContext storeContext;
  private final StoreScopeHolder storeScopeHolder;

  private static final String HEADER_STORE_ID = "X-Store-ID";

  @Override
  public boolean preHandle(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull Object handler) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    boolean authenticated = authentication instanceof JwtAuthenticationToken;
    String header = request.getHeader(HEADER_STORE_ID);
    if (StringUtils.isNumeric(header)) {
      if (authenticated) {
        if (!PermissionHolderGuard.holdsPermission(authentication)) {
          throw new AccessDeniedException("店舗文脈を名乗る権限がありません");
        }
        StoreScope scope = storeScopeHolder.current();
        if (scope == null) {
          throw new AccessDeniedException("授権店舗集合を解決できません");
        }
        Long storeId = parseStoreId(header);
        if (!scope.authorizes(storeId)) {
          throw new AccessDeniedException("この店舗の権限がありません");
        }
        storeContext.setStoreId(storeId);
        return true;
      }
      storeContext.setStoreId(parseStoreId(header));
      return true;
    }
    if (authenticated && StringUtils.isNotBlank(header)) {
      throw new AccessDeniedException("店舗文脈を名乗る権限がありません");
    }
    // 店舗文脈が無いまま素通しさせると storeFilter が有効化されず @StoreScoped クエリが全店舗の行を返す（fail-open）ため、
    // @StoreOptional を明示したエンドポイントに限り許可し、それ以外は 403 で拒否する。
    if (handler instanceof HandlerMethod handlerMethod
        && handlerMethod.hasMethodAnnotation(StoreOptional.class)) {
      return true;
    }
    throw new AccessDeniedException("店舗文脈が解決できません");
  }

  /** 全桁数字の文字列を long に変換する。long 範囲を超える場合は 400。 */
  private static Long parseStoreId(String numericValue) {
    try {
      return Long.parseLong(numericValue);
    } catch (NumberFormatException e) {
      throw new ServiceException("店舗 ID の形式が正しくありません");
    }
  }

  @Override
  public void afterCompletion(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull Object handler,
      @Nullable Exception ex) {
    storeContext.clear();
  }
}
