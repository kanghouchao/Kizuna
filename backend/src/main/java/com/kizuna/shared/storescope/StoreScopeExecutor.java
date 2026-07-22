package com.kizuna.shared.storescope;

import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 平台側（{@code /platform/**}）で明示的単店の店舗文脈を確立して action を実行する実行 seam（#429）。
 *
 * <p>{@code /platform/**} は {@link StoreIdInterceptor} を通らないため、店舗文脈が要る平台側端点は authorize → {@link
 * StoreContext} set → finally clear の舞踏を手書きしていた（{@code PlatformOrderService.create} が唯一例だった）。
 * 金流票群（#386 等）で第二消費者がほぼ確実に出るため、その舞踏を本 seam に一本化する。
 *
 * <p>引数 storeId を {@link StoreScope#fromAuthentication} で解決した授権集合で検証し、解決不能または授権外なら
 * fail-closed（{@link AccessDeniedException}）で拒否する。検証通過後に {@link StoreContext} へ storeId を設定し、
 * try/finally で必ず消す。
 */
@Component
@RequiredArgsConstructor
public class StoreScopeExecutor {

  private final StoreContext storeContext;

  /** 授権検証済みの storeId 文脈下で action を実行し、復帰時（例外時含む）に必ず文脈を消す。 */
  public <T> T runInStore(Long storeId, Supplier<T> action) {
    StoreScope scope =
        StoreScope.fromAuthentication(SecurityContextHolder.getContext().getAuthentication());
    if (scope == null || !scope.authorizes(storeId)) {
      throw new AccessDeniedException("指定店舗はこのアカウントの授権店舗集合に含まれません");
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
