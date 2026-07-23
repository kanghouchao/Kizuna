package com.kizuna.shared.storescope;

import com.kizuna.shared.exception.ServiceException;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 平台側（{@code /platform/**}）で明示的単店の店舗文脈を確立して action を実行する実行 seam。
 *
 * <p>{@code /platform/**} は {@link StoreIdInterceptor} を通らないため、店舗文脈が要る平台側端点は authorize → 存在性検証 →
 * {@link StoreContext} set → finally clear を本 seam に一本化する。
 *
 * <p>平台側の店舗文脈確立は、{@code /store/**} のインターセプタ対（{@link StoreIdInterceptor} +
 * StoreExistenceInterceptor）と 対称に、授権と実在性の双方を保証する。引数 storeId を {@link StoreScope#fromAuthentication}
 * で解決した授権集合で検証し、解決不能または 授権外なら fail-closed（{@link AccessDeniedException} → 403）で拒否する。授権通過後、{@link
 * StoreExistenceCheck} で実在性を 検証し、実在しなければ {@link ServiceException}（400 — 制約違反が 500 に逃げるのは
 * bug）で拒否する。検証通過後に {@link StoreContext} へ storeId を 設定し、try/finally で必ず消す。
 */
@Component
@RequiredArgsConstructor
public class StoreScopeExecutor {

  private final StoreContext storeContext;
  private final StoreExistenceCheck storeExistenceCheck;

  /** 授権・実在検証済みの storeId 文脈下で action を実行し、復帰時（例外時含む）に必ず文脈を消す。 */
  public <T> T runInStore(Long storeId, Supplier<T> action) {
    StoreScope scope =
        StoreScope.fromAuthentication(SecurityContextHolder.getContext().getAuthentication());
    if (scope == null || !scope.authorizes(storeId)) {
      throw new AccessDeniedException("指定店舗はこのアカウントの授権店舗集合に含まれません");
    }
    if (!storeExistenceCheck.exists(storeId)) {
      throw new ServiceException("店舗が見つかりません");
    }
    try {
      storeContext.setStoreId(storeId);
      return action.get();
    } finally {
      // /platform は StoreIdInterceptor(afterCompletion clear)を通らないため、ここで必ず消す
      storeContext.clear();
    }
  }
}
