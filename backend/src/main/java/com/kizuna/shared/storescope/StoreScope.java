package com.kizuna.shared.storescope;

import java.util.Set;

/**
 * 認証主体の授権店舗集合（集合作用域）。要求ごとに DB から解決され {@link StoreScopeHolder} が保持する — トークンの claim には載せない。
 * 解決できない要求では保持器が null を返し、呼び出し側が fail-closed に拒否する。
 */
public record StoreScope(boolean allStores, Set<Long> storeIds) {

  /** 指定店舗がこの授権集合に含まれるか。 */
  public boolean authorizes(Long storeId) {
    return storeId != null && (allStores || storeIds.contains(storeId));
  }
}
