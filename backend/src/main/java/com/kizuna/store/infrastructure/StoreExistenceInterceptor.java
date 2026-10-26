package com.kizuna.store.infrastructure;

import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 店舗文脈（X-Store-ID）が確立済みのとき、その store_id が実在することを毎リクエスト 1 回検証するインターセプタ。
 *
 * <p>{@link com.kizuna.shared.storescope.StoreIdInterceptor} の後段に同一 mount（{@code /store/**}・{@code
 * /files/**}）で登録する。 実在性判定はポート {@link StoreExistenceCheck} に寄せる。
 *
 * <p>全店舗を授権された主体と未認証の公開店面は、実在しない store_id も名乗れる。外部キー任せでは制約違反が 500 になるため、本インターセプタが 400 を保証する。応答の成形は
 * {@link ServiceException} を送出して {@link com.kizuna.shared.exception.CommonExceptionHandler}
 * へ委ね、ここでは response へ直接書かない — 同じ status を返す経路が複数のワイヤ形を持つと、 呼出側が応答体の形を一つに決められなくなるため。
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
      throw new ServiceException("店舗が見つかりません");
    }
    return true;
  }
}
