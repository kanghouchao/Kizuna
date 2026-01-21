package com.kizuna.model.entity.tenant.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class SecurityModelsTests {

  @Test
  void userRolePermissionRelationships() {
    TenantUser user = new TenantUser();
    user.setId("u1");
    user.setNickname("nick");
    user.setEmail("u@example.com");
    user.setPassword("p");

    TenantRole role = new TenantRole();
    role.setId("r1");
    role.setName("ADMIN");

    TenantPermission perm = new TenantPermission();
    perm.setId("p1");
    perm.setName("TENANT_MANAGE");

    // wire relationships in memory
    role.setPermissions(Set.of(perm));
    user.setRoles(Set.of(role));
    perm.setRoles(Set.of(role));
    role.setUsers(Set.of(user));

    assertThat(user.getRoles()).extracting(TenantRole::getName).contains("ADMIN");
    assertThat(role.getPermissions())
        .extracting(TenantPermission::getName)
        .contains("TENANT_MANAGE");
    assertThat(role.getUsers()).extracting(TenantUser::getEmail).contains("u@example.com");
  }
}
