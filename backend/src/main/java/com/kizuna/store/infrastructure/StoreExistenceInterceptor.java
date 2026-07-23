package com.kizuna.store.infrastructure;

import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 店舗文脈（X-Store-ID）が確立済みのとき、その store_id が実在することを毎リクエスト 1 回検証するインターセプタ。
 *
 * <p>{@link com.kizuna.shared.storescope.StoreIdInterceptor} の後段に同一 mount（{@code /store/**}・{@code
 * /files/**}）で登録する。 実在性判定は跨モジュールポート {@link StoreExistenceCheck} に収束させ、平台側の {@link
 * com.kizuna.shared.storescope.StoreScopeExecutor} と同一の判定経路にする。
 *
 * <p>JWT の授権店舗集合は次回ログインまで陳旧化しうるため、存在しない storeId が文脈に載ることがある。純 FK 兜底だと制約違反が 500 に逃げてしまうため、 本インターセプタが
 * 400 の保証点となり、{@link com.kizuna.shared.exception.CommonExceptionHandler} と同じ {@code error} キーの JSON
 * ボディを返す（同一パッケージの {@code MaintenanceModeInterceptor} の先例に倣う）。
 *
 * <p>店舗文脈が無い（{@code @StoreOptional} の素通り）場合は検証対象外として許可する。
 */
@Component
@RequiredArgsConstructor
public class StoreExistenceInterceptor implements HandlerInterceptor {

  private final StoreContext storeContext;
  private final StoreExistenceCheck storeExistenceCheck;

  @Override
  public boolean preHandle(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull Object handler)
      throws Exception {
    if (storeContext.hasStoreId() && !storeExistenceCheck.exists(storeContext.getStoreId())) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setCharacterEncoding(StandardCharsets.UTF_8.name());
      response.getWriter().write("{\"error\":\"店舗が見つかりません\"}");
      return false;
    }
    return true;
  }
}
