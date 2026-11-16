package com.kizuna.user.application;

import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.shared.web.JwtClaims;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 認証済みトークンの利用者 ID から、要求ごとに店舗作用域（{@link StoreScope}）を DB で解決する。トークンには載せないので、店舗集合の再割当は次の要求から効く。
 *
 * <p>利用者 ID の欠落、行の不在、退会済み、ロールで授権されない種別（CAST / MEMBER）はどれも null になり、呼び出し側が fail-closed に拒否する。
 *
 * <p>電子メールで引かないのは、退会で解放された電子メールを後継の身分が引き継ぎうるため（ADR 0029）。
 */
@Service
@RequiredArgsConstructor
public class StoreScopeResolver {

  private final UserRepository users;

  @Transactional(readOnly = true)
  public StoreScope resolve(Jwt jwt) {
    if (!(jwt.getClaims().get(JwtClaims.USER_ID) instanceof Number userId)) {
      return null;
    }
    return users
        .findById(userId.longValue())
        .filter(user -> !user.isWithdrawn())
        .filter(user -> user.getUserType().holdsRoles())
        .map(StoreScopeResolver::scopeOf)
        .orElse(null);
  }

  static StoreScope scopeOf(User user) {
    return new StoreScope(Set.copyOf(user.getStoreIds()));
  }
}
