package com.kizuna.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.kizuna.config.interceptor.TenantContext;
import com.kizuna.model.entity.central.security.CentralPermission;
import com.kizuna.model.entity.central.security.CentralRole;
import com.kizuna.model.entity.central.security.CentralUser;
import com.kizuna.model.entity.tenant.security.TenantPermission;
import com.kizuna.model.entity.tenant.security.TenantRole;
import com.kizuna.model.entity.tenant.security.TenantUser;
import com.kizuna.repository.central.CentralUserRepository;
import com.kizuna.repository.tenant.TenantUserRepository;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

  @Mock CentralUserRepository centralUserRepository;
  @Mock TenantUserRepository tenantUserRepository;
  @Mock TenantContext tenantContext;

  @InjectMocks CustomUserDetailsService customUserDetailsService;

  private CentralUser centralUser;
  private TenantUser tenantUser;

  @BeforeEach
  void setUp() {
    // Setup CentralUser
    CentralPermission centralPerm1 = new CentralPermission();
    centralPerm1.setName("READ");
    CentralPermission centralPerm2 = new CentralPermission();
    centralPerm2.setName("WRITE");

    CentralRole centralRole = new CentralRole();
    centralRole.setName("ADMIN");
    centralRole.setPermissions(Set.of(centralPerm1, centralPerm2));

    centralUser = new CentralUser();
    centralUser.setUsername("centraladmin");
    centralUser.setPassword("password");
    centralUser.setEnabled(true);
    centralUser.setRoles(Set.of(centralRole));

    // Setup TenantUser
    TenantPermission tenantPerm1 = new TenantPermission();
    tenantPerm1.setName("VIEW");
    TenantPermission tenantPerm2 = new TenantPermission();
    tenantPerm2.setName("EDIT");

    TenantRole tenantRole = new TenantRole();
    tenantRole.setName("USER");
    tenantRole.setPermissions(Set.of(tenantPerm1, tenantPerm2));

    tenantUser = new TenantUser();
    tenantUser.setEmail("tenantuser@test.com");
    tenantUser.setPassword("password");
    tenantUser.setEnabled(true);
    tenantUser.setRoles(Set.of(tenantRole));
  }

  @Test
  void loadUserByUsername_centralUser() {
    when(tenantContext.isTenant()).thenReturn(false);
    when(centralUserRepository.findByUsername("centraladmin")).thenReturn(Optional.of(centralUser));

    UserDetails userDetails = customUserDetailsService.loadUserByUsername("centraladmin");

    assertThat(userDetails.getUsername()).isEqualTo("centraladmin");
    assertThat(userDetails.getPassword()).isEqualTo("password");
    assertThat(userDetails.isEnabled()).isTrue();
    assertThat(userDetails.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder("ROLE_ADMIN", "READ", "PERM_READ", "WRITE", "PERM_WRITE");
  }

  @Test
  void loadUserByUsername_tenantUser() {
    when(tenantContext.isTenant()).thenReturn(true);
    when(tenantUserRepository.findByEmail("tenantuser@test.com"))
        .thenReturn(Optional.of(tenantUser));

    UserDetails userDetails = customUserDetailsService.loadUserByUsername("tenantuser@test.com");

    assertThat(userDetails.getUsername()).isEqualTo("tenantuser@test.com");
    assertThat(userDetails.getPassword()).isEqualTo("password");
    assertThat(userDetails.isEnabled()).isTrue();
    assertThat(userDetails.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder("ROLE_USER", "VIEW", "PERM_VIEW", "EDIT", "PERM_EDIT");
  }

  @Test
  void loadUserByUsername_centralUserNotFound() {
    when(tenantContext.isTenant()).thenReturn(false);
    when(centralUserRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("nonexistent"))
        .isInstanceOf(UsernameNotFoundException.class)
        .hasMessageContaining("User not found (central): nonexistent");
  }

  @Test
  void loadUserByUsername_tenantUserNotFound() {
    when(tenantContext.isTenant()).thenReturn(true);
    when(tenantUserRepository.findByEmail("nonexistent@test.com")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("nonexistent@test.com"))
        .isInstanceOf(UsernameNotFoundException.class)
        .hasMessageContaining("User not found (tenant): nonexistent@test.com");
  }

  @Test
  void buildAuthorities_withEmptyRoles() {
    CentralUser userWithNoRoles = new CentralUser();
    userWithNoRoles.setUsername("noroles");
    userWithNoRoles.setPassword("password");
    userWithNoRoles.setEnabled(true);
    userWithNoRoles.setRoles(Collections.emptySet());

    when(tenantContext.isTenant()).thenReturn(false);
    when(centralUserRepository.findByUsername("noroles")).thenReturn(Optional.of(userWithNoRoles));

    UserDetails userDetails = customUserDetailsService.loadUserByUsername("noroles");
    assertThat(userDetails.getAuthorities()).isEmpty();
  }

  @Test
  void buildAuthorities_withNullPermissionNames() {
    CentralPermission centralPerm = new CentralPermission();
    centralPerm.setName(null);
    CentralRole centralRole = new CentralRole();
    centralRole.setName("TEST_ROLE");
    centralRole.setPermissions(Set.of(centralPerm));

    CentralUser userWithNullPermName = new CentralUser();
    userWithNullPermName.setUsername("nullperm");
    userWithNullPermName.setPassword("password");
    userWithNullPermName.setEnabled(true);
    userWithNullPermName.setRoles(Set.of(centralRole));

    when(tenantContext.isTenant()).thenReturn(false);
    when(centralUserRepository.findByUsername("nullperm"))
        .thenReturn(Optional.of(userWithNullPermName));

    UserDetails userDetails = customUserDetailsService.loadUserByUsername("nullperm");
    assertThat(userDetails.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder("ROLE_TEST_ROLE", "PERM_");
  }

  @Test
  void buildAuthorities_withNullRoleName() {
    CentralRole centralRole = new CentralRole();
    centralRole.setName(null);
    centralRole.setPermissions(Collections.emptySet());

    CentralUser userWithNullRoleName = new CentralUser();
    userWithNullRoleName.setUsername("nullrole");
    userWithNullRoleName.setPassword("password");
    userWithNullRoleName.setEnabled(true);
    userWithNullRoleName.setRoles(Set.of(centralRole));

    when(tenantContext.isTenant()).thenReturn(false);
    when(centralUserRepository.findByUsername("nullrole"))
        .thenReturn(Optional.of(userWithNullRoleName));

    UserDetails userDetails = customUserDetailsService.loadUserByUsername("nullrole");
    assertThat(userDetails.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder("ROLE_");
  }
}
