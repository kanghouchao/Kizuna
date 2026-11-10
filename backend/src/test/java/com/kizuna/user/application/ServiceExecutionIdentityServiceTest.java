package com.kizuna.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.shared.storescope.StoreScope;
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
  void serviceIsJudgedByPermissionAndCarriesItsStoreSet() {
    var users = mock(UserRepository.class);
    var roles = mock(RoleRepository.class);
    var specific = user(31L, UserType.SERVICE, StoreScopeType.SPECIFIC_STORES, Set.of(1L));
    var allStores = user(32L, UserType.SERVICE, StoreScopeType.ALL_STORES, Set.of());
    when(users.findById(31L)).thenReturn(Optional.of(specific));
    when(users.findById(32L)).thenReturn(Optional.of(allStores));
    when(roles.findIdsByPermissionCode("TASK_EXECUTE")).thenReturn(Set.of(7L));
    var service = new ServiceExecutionIdentityService(users, roles);

    var scoped = service.requireServiceScope(31L, PermissionCode.TASK_EXECUTE);
    assertThat(scoped.actor().id()).isEqualTo(31L);
    assertThat(scoped.stores()).isEqualTo(new StoreScope(false, Set.of(1L)));
    assertThat(service.requireServiceScope(32L, PermissionCode.TASK_EXECUTE).stores().allStores())
        .isTrue();
    assertThat(service.requireService(31L, PermissionCode.TASK_EXECUTE).id()).isEqualTo(31L);

    when(roles.findIdsByPermissionCode("TASK_EXECUTE")).thenReturn(Set.of());
    assertThatThrownBy(() -> service.requireService(31L, PermissionCode.TASK_EXECUTE))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void operatorNeedsOnlyThePermissionNotAllStores() {
    var users = mock(UserRepository.class);
    var roles = mock(RoleRepository.class);
    var staff = user(41L, UserType.STAFF, StoreScopeType.SPECIFIC_STORES, Set.of(1L));
    when(users.findByEmail("op@kizuna.test")).thenReturn(Optional.of(staff));
    when(roles.findIdsByPermissionCode("TASK_MANAGE")).thenReturn(Set.of(7L));
    var service = new ServiceExecutionIdentityService(users, roles);

    assertThat(service.requireOperator("op@kizuna.test", PermissionCode.TASK_MANAGE).id())
        .isEqualTo(41L);
    assertThatThrownBy(() -> service.requireOperator("op@kizuna.test", PermissionCode.AUDIT_VIEW))
        .isInstanceOf(AccessDeniedException.class);
  }

  private static User user(Long id, UserType type, StoreScopeType scope, Set<Long> storeIds) {
    var user =
        User.builder()
            .displayName("失効処理")
            .userType(type)
            .email(type == UserType.SERVICE ? null : "op@kizuna.test")
            .password(type == UserType.SERVICE ? null : "hash")
            .enabled(true)
            .roleIds(Set.of(7L))
            .storeScopeType(scope)
            .storeIds(storeIds)
            .build();
    user.setId(id);
    return user;
  }
}
