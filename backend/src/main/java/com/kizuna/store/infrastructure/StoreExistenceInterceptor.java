package com.kizuna.store.infrastructure;

import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.store.domain.StoreRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 店舗文脈（X-Store-ID）が確立済みのとき、その store_id が実在することを毎リクエスト 1 回検証するインターセプタ（#429）。
 *
 * <p>{@link com.kizuna.shared.storescope.StoreIdInterceptor} の後段に同一 mount（{@code /store/**}・{@code
 * /files/**}）で登録する。各サービスの create にあった {@code storeRepository.findById(...).orElseThrow} 様板を置き換える。
 *
 * <p>JWT の授権店舗集合は次回ログインまで陳旧化しうるため、存在しない storeId が文脈に載ることがある。純 FK 兜底だと制約違反が 500 に逃げ、#398 裁定「制約違反が
 * 500 に逃げるのは bug」と矛盾する。本インターセプタが 400 の保証点となる。
 *
 * <p>店舗文脈が無い（{@code @StoreOptional} の素通り）場合は検証対象外として許可する。 shared→store の逆依存を避けるため shared
 * には置かず、StoreRepository を参照できる store モジュールに配置する。
 */
@Component
@RequiredArgsConstructor
public class StoreExistenceInterceptor implements HandlerInterceptor {

  private final StoreContext storeContext;
  private final StoreRepository storeRepository;

  @Override
  public boolean preHandle(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull Object handler) {
    if (storeContext.hasStoreId() && !storeRepository.existsById(storeContext.getStoreId())) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return false;
    }
    return true;
  }
}
