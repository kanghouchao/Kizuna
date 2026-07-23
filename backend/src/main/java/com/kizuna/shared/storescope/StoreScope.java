package com.kizuna.shared.storescope;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 認証済み平台トークンの授権店舗集合（集合作用域）。JWT の storeScopeType / storeIds claim から解決する。 解決できない場合は null を返し、呼び出し側が
 * fail-closed に拒否する。
 */
public record StoreScope(boolean allStores, Set<Long> storeIds) {

  public static StoreScope fromAuthentication(Authentication authentication) {
    Jwt jwt = AuthenticatedJwt.from(authentication);
    if (jwt == null) {
      return null;
    }
    String scopeType = jwt.getClaimAsString("storeScopeType");
    if ("ALL_STORES".equals(scopeType)) {
      return new StoreScope(true, Set.of());
    }
    if ("SPECIFIC_STORES".equals(scopeType)) {
      Object raw = jwt.getClaim("storeIds");
      if (!(raw instanceof List<?> list) || list.isEmpty()) {
        return null;
      }
      Set<Long> ids =
          list.stream()
              .filter(Number.class::isInstance)
              .map(v -> ((Number) v).longValue())
              .collect(Collectors.toUnmodifiableSet());
      if (ids.size() != list.size()) {
        return null;
      }
      return new StoreScope(false, ids);
    }
    return null;
  }

  /** 指定店舗がこの授権集合に含まれるか。 */
  public boolean authorizes(Long storeId) {
    return storeId != null && (allStores || storeIds.contains(storeId));
  }
}
