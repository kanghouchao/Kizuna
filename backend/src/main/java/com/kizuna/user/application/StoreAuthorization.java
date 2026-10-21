package com.kizuna.user.application;

import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.User;
import jakarta.persistence.criteria.Subquery;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;

/**
 * ユーザー一覧の述語を店舗作用域（{@link PlatformUser}）で絞る。作用域は別集約なので、User の root からは相関 exists で結ぶ —
 * 親行を結合で増やさず、ページングの件数・境界を保つ。
 */
final class StoreAuthorization {

  private StoreAuthorization() {}

  /** 指定店舗を担当範囲に含む（ALL_STORES、または個別集合のメンバー）。 */
  static Specification<User> authorizes(Long storeId) {
    return (root, query, cb) -> {
      Subquery<Long> scope = query.subquery(Long.class);
      var p = scope.from(PlatformUser.class);
      scope
          .select(p.get("userId"))
          .where(
              cb.equal(p.get("userId"), root.get("id")),
              cb.or(
                  cb.equal(p.get("storeScopeType"), StoreScopeType.ALL_STORES),
                  cb.isMember(storeId, p.<Set<Long>>get("storeIds"))));
      return cb.exists(scope);
    };
  }

  /** 担当店舗を個別指定している（SPECIFIC_STORES）。 */
  static Specification<User> specificStores() {
    return scopeTypeIs(StoreScopeType.SPECIFIC_STORES);
  }

  /** 全店舗を担当している（ALL_STORES）。 */
  static Specification<User> allStores() {
    return scopeTypeIs(StoreScopeType.ALL_STORES);
  }

  private static Specification<User> scopeTypeIs(StoreScopeType type) {
    return (root, query, cb) -> {
      Subquery<Long> scope = query.subquery(Long.class);
      var p = scope.from(PlatformUser.class);
      scope
          .select(p.get("userId"))
          .where(
              cb.equal(p.get("userId"), root.get("id")), cb.equal(p.get("storeScopeType"), type));
      return cb.exists(scope);
    };
  }
}
