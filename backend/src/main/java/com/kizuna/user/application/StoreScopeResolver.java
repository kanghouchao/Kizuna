package com.kizuna.user.application;

import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 認証済みトークンの主体から、要求ごとに店舗作用域（{@link StoreScope}）を DB で解決する。トークンには載せないので、店舗集合の再割当は
 * 次の要求から効く。ロールで授権されない種別（CAST / MEMBER）は本人の行に店舗を持っていても作用域を持たず null になり、 呼び出し側が fail-closed に拒否する。
 */
@Service
@RequiredArgsConstructor
public class StoreScopeResolver {

  private final UserRepository users;

  @Transactional(readOnly = true)
  public StoreScope resolve(Jwt jwt) {
    return users
        .findByEmail(jwt.getSubject())
        .filter(user -> user.getUserType().holdsRoles())
        .map(StoreScopeResolver::scopeOf)
        .orElse(null);
  }

  private static StoreScope scopeOf(User user) {
    if (user.getStoreScopeType() == StoreScopeType.ALL_STORES) {
      return new StoreScope(true, Set.of());
    }
    return new StoreScope(false, Set.copyOf(user.getStoreIds()));
  }
}
