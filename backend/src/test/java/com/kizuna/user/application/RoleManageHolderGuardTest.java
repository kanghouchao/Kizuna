package com.kizuna.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.user.domain.LastRoleManageHolderException;
import com.kizuna.user.domain.PermissionRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.UserType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoleManageHolderGuardTest {
  @Mock private PlatformUserRepository repository;
  @Mock private RoleRepository roleRepository;
  @Mock private PermissionRepository permissionRepository;
  @InjectMocks private RoleManageHolderGuard guard;

  @Test
  void grantChange_countsFreshHoldersAfterTheMutexAndPopulationLocks() {
    PlatformUser target =
        PlatformUser.builder()
            .email("guard@kizuna.test")
            .password("hash")
            .displayName("管理者")
            .enabled(true)
            .userType(UserType.STAFF)
            .roleIds(Set.of(10L))
            .build();
    target.setId(1L);
    when(roleRepository.findIdsByPermissionCode("ROLE_MANAGE")).thenReturn(Set.of(10L));
    when(repository.lockEnabledRoleHolderIds(Set.of(10L))).thenReturn(List.of(1L, 2L));
    when(repository.findEnabledRoleHolderIds(Set.of(10L))).thenReturn(List.of(1L));

    assertThatThrownBy(() -> guard.requireAfterGrantChange(target, Set.of(20L)))
        .isInstanceOf(LastRoleManageHolderException.class)
        .hasMessage("最後の管理権限保持者を停止・降格することはできません");

    var order = inOrder(permissionRepository, roleRepository, repository);
    order.verify(permissionRepository).lockIdByCode("ROLE_MANAGE");
    order.verify(roleRepository).findIdsByPermissionCode("ROLE_MANAGE");
    order.verify(repository).lockEnabledRoleHolderIds(Set.of(10L));
    order.verify(repository).findEnabledRoleHolderIds(Set.of(10L));
  }

  @Test
  void suspension_loadsTheTargetAfterTheMutexAndCountsFreshHolders() {
    PlatformUser target =
        PlatformUser.builder()
            .email("guard@kizuna.test")
            .password("hash")
            .displayName("管理者")
            .enabled(true)
            .userType(UserType.STAFF)
            .roleIds(Set.of(10L))
            .build();
    target.setId(1L);
    when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(target));
    when(roleRepository.findIdsByPermissionCode("ROLE_MANAGE")).thenReturn(Set.of(10L));
    when(repository.lockEnabledRoleHolderIds(Set.of(10L))).thenReturn(List.of(1L, 2L));
    when(repository.findEnabledRoleHolderIds(Set.of(10L))).thenReturn(List.of(1L));

    assertThatThrownBy(() -> guard.loadForSuspension(1L))
        .isInstanceOf(LastRoleManageHolderException.class);

    var order = inOrder(permissionRepository, roleRepository, repository);
    order.verify(permissionRepository).lockIdByCode("ROLE_MANAGE");
    order.verify(repository).findByIdForUpdate(1L);
    order.verify(roleRepository).findIdsByPermissionCode("ROLE_MANAGE");
    order.verify(repository).lockEnabledRoleHolderIds(Set.of(10L));
    order.verify(repository).findEnabledRoleHolderIds(Set.of(10L));
  }

  @Test
  void roleRemoval_locksThePopulationBeforeCheckingRemainingSuppliers() {
    when(roleRepository.findIdsByPermissionCode("ROLE_MANAGE")).thenReturn(Set.of(10L, 20L));
    when(repository.findEnabledRoleHolderIds(Set.of(20L))).thenReturn(List.of());
    when(repository.findEnabledRoleHolderIds(Set.of(10L))).thenReturn(List.of(1L));

    assertThatThrownBy(() -> guard.requireAfterRolePermissionRemoval(10L))
        .isInstanceOf(LastRoleManageHolderException.class)
        .hasMessage("最後の管理権限保持者が居なくなるため、このロールから管理権限を外すことはできません");

    var order = inOrder(permissionRepository, roleRepository, repository);
    order.verify(permissionRepository).lockIdByCode("ROLE_MANAGE");
    order.verify(roleRepository).findIdsByPermissionCode("ROLE_MANAGE");
    order.verify(repository).lockEnabledRoleHolderIds(Set.of(10L, 20L));
    order.verify(repository).findEnabledRoleHolderIds(Set.of(20L));
    order.verify(repository).findEnabledRoleHolderIds(Set.of(10L));
  }

  @Test
  void grantChange_usesTheSuppliersAfterAcquiringTheMutex() {
    when(permissionRepository.lockIdByCode("ROLE_MANAGE"))
        .thenAnswer(
            invocation -> {
              when(roleRepository.findIdsByPermissionCode("ROLE_MANAGE")).thenReturn(Set.of(20L));
              return Optional.of(1L);
            });

    guard.requireAfterGrantChange(target(Set.of(10L)), Set.of(20L));

    verify(repository, never()).lockEnabledRoleHolderIds(any());
    verify(repository, never()).findEnabledRoleHolderIds(any());
  }

  @Test
  void grantChange_retainingAnotherSupplierDoesNotLockThePopulation() {
    when(roleRepository.findIdsByPermissionCode("ROLE_MANAGE")).thenReturn(Set.of(10L, 20L));

    guard.requireAfterGrantChange(target(Set.of(10L, 20L)), Set.of(20L));

    verifyNoInteractions(repository);
  }

  @Test
  void grantChange_allowsDemotionWhenAnotherHolderRemains() {
    when(roleRepository.findIdsByPermissionCode("ROLE_MANAGE")).thenReturn(Set.of(10L));
    when(repository.findEnabledRoleHolderIds(Set.of(10L))).thenReturn(List.of(1L, 2L));

    guard.requireAfterGrantChange(target(Set.of(10L)), Set.of(20L));
  }

  @Test
  void grantChange_emptySuppliersNeverReachAnInQuery() {
    when(roleRepository.findIdsByPermissionCode("ROLE_MANAGE")).thenReturn(Set.of());

    guard.requireAfterGrantChange(target(Set.of(10L)), Set.of(20L));

    verifyNoInteractions(repository);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void suspension_returnsNonHoldersAndDisabledTargetsWithoutPopulationLocks(boolean enabled) {
    PlatformUser target = target(Set.of(20L));
    if (!enabled) {
      target.stop();
    } else {
      when(roleRepository.findIdsByPermissionCode("ROLE_MANAGE")).thenReturn(Set.of(10L));
    }
    when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(target));

    assertThat(guard.loadForSuspension(1L)).isSameAs(target);

    var order = inOrder(permissionRepository, repository);
    order.verify(permissionRepository).lockIdByCode("ROLE_MANAGE");
    order.verify(repository).findByIdForUpdate(1L);
    verify(repository, never()).lockEnabledRoleHolderIds(any());
    verify(repository, never()).findEnabledRoleHolderIds(any());
    if (!enabled) {
      verifyNoInteractions(roleRepository);
    }
  }

  @Test
  void suspension_missingTargetKeepsTheAccountNotFoundMessage() {
    assertThatThrownBy(() -> guard.loadForSuspension(404L))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("アカウントが見つかりません: 404");
  }

  @Test
  void suspension_allowsAHolderWhenAnotherRemains() {
    PlatformUser target = target(Set.of(10L));
    when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(target));
    when(roleRepository.findIdsByPermissionCode("ROLE_MANAGE")).thenReturn(Set.of(10L));
    when(repository.findEnabledRoleHolderIds(Set.of(10L))).thenReturn(List.of(1L, 2L));

    assertThat(guard.loadForSuspension(1L)).isSameAs(target);
    assertThat(target.getEnabled()).isTrue();
  }

  @Test
  void roleRemoval_reevaluatesSuppliersAfterTheMutex() {
    when(permissionRepository.lockIdByCode("ROLE_MANAGE"))
        .thenAnswer(
            invocation -> {
              when(roleRepository.findIdsByPermissionCode("ROLE_MANAGE")).thenReturn(Set.of());
              return Optional.of(1L);
            });

    guard.requireAfterRolePermissionRemoval(10L);

    verifyNoInteractions(repository);
  }

  @Test
  void roleRemoval_allowsTheSamePersonToRetainAnotherSupplier() {
    when(roleRepository.findIdsByPermissionCode("ROLE_MANAGE")).thenReturn(Set.of(10L, 20L));
    when(repository.findEnabledRoleHolderIds(Set.of(20L))).thenReturn(List.of(1L));

    guard.requireAfterRolePermissionRemoval(10L);

    verify(repository).lockEnabledRoleHolderIds(Set.of(10L, 20L));
  }

  @Test
  void roleRemoval_allowsAnAlreadyEmptyPopulationWithoutQueryingEmptySuppliers() {
    when(roleRepository.findIdsByPermissionCode("ROLE_MANAGE")).thenReturn(Set.of(10L));
    when(repository.findEnabledRoleHolderIds(Set.of(10L))).thenReturn(List.of());

    guard.requireAfterRolePermissionRemoval(10L);

    verify(repository, never()).findEnabledRoleHolderIds(Set.of());
  }

  private PlatformUser target(Set<Long> roleIds) {
    PlatformUser target =
        PlatformUser.builder()
            .email("guard@kizuna.test")
            .password("hash")
            .displayName("管理者")
            .enabled(true)
            .userType(UserType.STAFF)
            .roleIds(roleIds)
            .build();
    target.setId(1L);
    return target;
  }
}
