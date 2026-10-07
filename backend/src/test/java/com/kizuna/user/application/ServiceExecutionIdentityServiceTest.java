package com.kizuna.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class ServiceExecutionIdentityServiceTest {
  @Test
  void platformWorkRequiresExplicitAllStoresAndCurrentPermission() {
    var users = mock(PlatformUserRepository.class);
    var roles = mock(RoleRepository.class);
    var user =
        PlatformUser.builder()
            .displayName("失効処理")
            .userType(UserType.SERVICE)
            .enabled(true)
            .roleIds(Set.of(7L))
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(1L))
            .build();
    user.setId(31L);
    when(users.findById(31L)).thenReturn(Optional.of(user));
    when(roles.findIdsByPermissionCode("TASK_EXECUTE")).thenReturn(Set.of(7L));
    var service = new ServiceExecutionIdentityService(users, roles);
    assertThatThrownBy(() -> service.requireService(31L, PermissionCode.TASK_EXECUTE, null))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(service.requireService(31L, PermissionCode.TASK_EXECUTE, 1L).id()).isEqualTo(31L);
    when(roles.findIdsByPermissionCode("TASK_EXECUTE")).thenReturn(Set.of());
    assertThatThrownBy(() -> service.requireService(31L, PermissionCode.TASK_EXECUTE, 1L))
        .isInstanceOf(AccessDeniedException.class);
  }
}
