package com.kizuna.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class ServiceExecutionIdentityServiceTest {
  @Test
  void groupWideWorkRequiresExplicitAllStoresAndCurrentPermission() {
    var users = mock(UserRepository.class);
    var roles = mock(RoleRepository.class);
    var specific = service(31L, StoreScopeType.SPECIFIC_STORES, Set.of(1L));
    var allStores = service(32L, StoreScopeType.ALL_STORES, Set.of());
    when(users.findById(31L)).thenReturn(Optional.of(specific));
    when(users.findById(32L)).thenReturn(Optional.of(allStores));
    when(roles.findIdsByPermissionCode("TASK_EXECUTE")).thenReturn(Set.of(7L));
    var service = new ServiceExecutionIdentityService(users, roles);
    assertThatThrownBy(() -> service.requireService(31L, PermissionCode.TASK_EXECUTE))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(service.requireService(32L, PermissionCode.TASK_EXECUTE).id()).isEqualTo(32L);
    when(roles.findIdsByPermissionCode("TASK_EXECUTE")).thenReturn(Set.of());
    assertThatThrownBy(() -> service.requireService(32L, PermissionCode.TASK_EXECUTE))
        .isInstanceOf(AccessDeniedException.class);
  }

  private static User service(Long id, StoreScopeType scope, Set<Long> storeIds) {
    var user =
        User.builder()
            .displayName("失効処理")
            .userType(UserType.SERVICE)
            .enabled(true)
            .roleIds(Set.of(7L))
            .storeScopeType(scope)
            .storeIds(storeIds)
            .build();
    user.setId(id);
    return user;
  }
}
