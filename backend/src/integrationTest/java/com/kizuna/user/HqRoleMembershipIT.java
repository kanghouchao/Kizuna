package com.kizuna.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.user.application.HqRoleMembership;
import com.kizuna.user.domain.PermissionRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.Role;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

class HqRoleMembershipIT extends CrossStoreTestSupport {
  @Autowired private PlatformUserRepository users;
  @Autowired private RoleRepository roles;
  @Autowired private PermissionRepository permissions;

  @Test
  @Transactional
  void specificationsAndMemoryAgreeIncludingDisabledAndEmptyMembership() {
    Long hq = role("STAFF_ACCOUNT_MANAGE");
    Long store = role("ORDER_MANAGE");
    PlatformUser mixed = staff(Set.of(hq, store), true);
    PlatformUser disabled = staff(Set.of(hq), false);
    PlatformUser storeOnly = staff(Set.of(store), true);
    PlatformUser empty =
        users.saveAndFlush(
            PlatformUser.builder()
                .email(UUID.randomUUID() + "@kizuna.test")
                .password("hash")
                .displayName("会員")
                .userType(UserType.MEMBER)
                .roleIds(Set.of())
                .storeScopeType(StoreScopeType.SPECIFIC_STORES)
                .storeIds(Set.of())
                .build());
    List<PlatformUser> fixtures = List.of(mixed, disabled, storeOnly, empty);
    Set<Long> fixtureIds =
        Set.of(mixed.getId(), disabled.getId(), storeOnly.getId(), empty.getId());
    Specification<PlatformUser> fixtureScope = (root, query, cb) -> root.get("id").in(fixtureIds);

    for (Set<Long> hqIds : List.of(roles.findHqRoleIds(), Set.<Long>of())) {
      HqRoleMembership membership = new HqRoleMembership(hqIds);
      List<Long> matches =
          users.findAll(fixtureScope.and(membership.holders())).stream()
              .map(PlatformUser::getId)
              .toList();
      List<Long> nonMatches =
          users.findAll(fixtureScope.and(membership.nonHolders())).stream()
              .map(PlatformUser::getId)
              .toList();
      assertThat(matches)
          .containsExactlyInAnyOrderElementsOf(
              hqIds.isEmpty() ? List.of() : List.of(mixed.getId(), disabled.getId()));
      assertThat(nonMatches)
          .containsExactlyInAnyOrderElementsOf(
              hqIds.isEmpty() ? fixtureIds : Set.of(storeOnly.getId(), empty.getId()));
      for (PlatformUser user : fixtures) {
        assertThat(membership.holdsAny(user.getRoleIds()))
            .isEqualTo(matches.contains(user.getId()));
      }
    }
  }

  private Long role(String code) {
    Set<Long> ids = Set.of(permissions.findByCodeIn(Set.of(code)).getFirst().getId());
    return roles
        .saveAndFlush(Role.builder().name("HQ判定_" + UUID.randomUUID()).permissionIds(ids).build())
        .getId();
  }

  private PlatformUser staff(Set<Long> roleIds, boolean enabled) {
    return users.saveAndFlush(
        PlatformUser.builder()
            .email(UUID.randomUUID() + "@kizuna.test")
            .password("hash")
            .displayName("判定対象")
            .enabled(enabled)
            .userType(UserType.STAFF)
            .roleIds(roleIds)
            .storeScopeType(StoreScopeType.ALL_STORES)
            .storeIds(Set.of())
            .build());
  }
}
