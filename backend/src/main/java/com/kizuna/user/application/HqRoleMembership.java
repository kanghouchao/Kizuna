package com.kizuna.user.application;

import com.kizuna.user.domain.PlatformUser;
import jakarta.persistence.criteria.Predicate;
import java.util.Collections;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;

/** HQ 側ロールの保持だけを判定する。状態・本人種別・店舗範囲の制限は各管理面が担う。 */
public final class HqRoleMembership {
  private final Set<Long> roleIds;

  public HqRoleMembership(Set<Long> roleIds) {
    this.roleIds = Set.copyOf(roleIds);
  }

  public Specification<PlatformUser> holders() {
    // member of は相関 exists となり、複数ロールを持つ親行も増やさずページングを保つ。
    return (root, query, cb) ->
        cb.or(
            roleIds.stream()
                .map(roleId -> cb.isMember(roleId, root.<Set<Long>>get("roleIds")))
                .toArray(Predicate[]::new));
  }

  public Specification<PlatformUser> nonHolders() {
    return Specification.not(holders());
  }

  public boolean holdsAny(Set<Long> heldRoleIds) {
    return !Collections.disjoint(heldRoleIds, roleIds);
  }
}
