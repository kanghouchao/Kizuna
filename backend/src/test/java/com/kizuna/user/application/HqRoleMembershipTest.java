package com.kizuna.user.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HqRoleMembershipTest {
  @Test
  void membership_isAnImmutableSnapshotAndMatchesAnyHqRole() {
    Set<Long> ids = new HashSet<>(Set.of(10L));
    HqRoleMembership membership = new HqRoleMembership(ids);
    ids.clear();

    assertThat(membership.holdsAny(Set.of(10L, 20L))).isTrue();
    assertThat(membership.holdsAny(Set.of(20L))).isFalse();
    assertThat(membership.holdsAny(Set.of())).isFalse();
    assertThat(new HqRoleMembership(Set.of()).holdsAny(Set.of(10L))).isFalse();
  }
}
