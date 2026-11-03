package com.kizuna.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.user.domain.InvalidCredentialAssignmentException;
import com.kizuna.user.domain.Role;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserCredentialsChanged;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CredentialOperationsTest {
  private final List<Object> events = new ArrayList<>();
  private final RoleManageHolderGuard holderGuard = mock(RoleManageHolderGuard.class);
  private final UserRepository userRepository = mock(UserRepository.class);
  private final EntityManager entityManager = mock(EntityManager.class);
  private final CredentialOperations operations =
      new CredentialOperations(events::add, holderGuard, userRepository, entityManager);

  @ParameterizedTest
  @EnumSource(
      value = UserType.class,
      names = {"STAFF", "CAST", "MEMBER"})
  void repeatedStopNotifiesTheSameVersion(UserType type) {
    User user = user(type);
    operations.stop(user);
    operations.stop(user);
    assertThat(user.getEnabled()).isFalse();
    assertThat(user.getCredentialVersion()).isEqualTo(1L);
    assertThat(events)
        .containsExactly(
            new UserCredentialsChanged(user.getId(), 1L),
            new UserCredentialsChanged(user.getId(), 1L));
  }

  @Test
  void serviceStopIsIdempotentWithoutNotification() {
    User user = user(UserType.SERVICE);
    operations.stop(user);
    operations.stop(user);
    assertThat(user.getEnabled()).isFalse();
    assertThat(user.getCredentialVersion()).isEqualTo(1L);
    assertThat(events).isEmpty();
  }

  @Test
  void passwordChangeAndExplicitInvalidationNotifyAdvancedVersions() {
    User user = user(UserType.STAFF);
    operations.changePassword(user, "encoded");
    assertThat(user.getPassword()).isEqualTo("encoded");
    operations.invalidateSessions(user);
    assertThat(user.getCredentialVersion()).isEqualTo(2L);
    assertThat(events)
        .containsExactly(
            new UserCredentialsChanged(user.getId(), 1L),
            new UserCredentialsChanged(user.getId(), 2L));
  }

  @Test
  void grantReassignmentNotifiesTheCurrentVersionOnEveryRequest() {
    User user = user(UserType.STAFF);
    operations.reassignGrants(user, Set.of(1L, 2L), StoreScopeType.SPECIFIC_STORES, Set.of(1L));
    operations.reassignGrants(user, Set.of(1L, 2L), StoreScopeType.ALL_STORES, Set.of());
    assertThat(user.getRoleIds()).containsExactlyInAnyOrder(1L, 2L);
    assertThat(user.getStoreScopeType()).isEqualTo(StoreScopeType.ALL_STORES);
    assertThat(user.getCredentialVersion()).isEqualTo(1L);
    assertThat(events)
        .containsExactly(
            new UserCredentialsChanged(user.getId(), 1L),
            new UserCredentialsChanged(user.getId(), 1L));
  }

  @Test
  void serviceGrantReassignmentAdvancesWithoutNotification() {
    User user = user(UserType.SERVICE);
    operations.reassignGrants(user, Set.of(2L), StoreScopeType.SPECIFIC_STORES, Set.of(1L));
    assertThat(user.getCredentialVersion()).isEqualTo(1L);
    assertThat(events).isEmpty();
  }

  @Test
  void serviceCannotReceivePassword() {
    User user = user(UserType.SERVICE);
    assertThatThrownBy(() -> operations.changePassword(user, "encoded"))
        .isInstanceOf(InvalidCredentialAssignmentException.class);
    assertThat(user.getCredentialVersion()).isZero();
    assertThat(events).isEmpty();
  }

  @Test
  void withdrawStopsMarksWithdrawnAndNotifiesTheAdvancedVersion() {
    User user = user(UserType.MEMBER);
    var at = OffsetDateTime.parse("2026-10-26T10:00:00+09:00");
    operations.withdraw(user, at);
    assertThat(user.getEnabled()).isFalse();
    assertThat(user.getWithdrawnAt()).isEqualTo(at);
    assertThat(user.getCredentialVersion()).isEqualTo(1L);
    assertThat(events).containsExactly(new UserCredentialsChanged(user.getId(), 1L));
  }

  @Test
  void rolePermissionChangeLocksHoldersAndAdvancesEveryHolderBeforeReplacing() {
    Role role = locked(role(Set.of(1L)));
    User staff = user(UserType.STAFF);
    User serviceIdentity = user(UserType.SERVICE);
    serviceIdentity.setId(43L);
    when(holderGuard.lockRoleHolders(7L)).thenReturn(List.of(staff, serviceIdentity));

    operations.replaceRolePermissions(role, Set.of(2L));

    assertThat(role.getPermissionIds()).containsExactly(2L);
    assertThat(staff.getCredentialVersion()).isEqualTo(1L);
    assertThat(serviceIdentity.getCredentialVersion()).isEqualTo(1L);
    assertThat(events).containsExactly(new UserCredentialsChanged(42L, 1L));
    var order = inOrder(holderGuard, userRepository);
    order.verify(holderGuard).lockRoleHolders(7L);
    order.verify(userRepository).saveAllAndFlush(List.of(staff, serviceIdentity));
  }

  @Test
  void unchangedRolePermissionsLeaveHoldersUntouched() {
    Role role = locked(role(Set.of(1L, 2L)));

    operations.replaceRolePermissions(role, Set.of(2L, 1L));

    assertThat(role.getPermissionIds()).containsExactlyInAnyOrder(1L, 2L);
    verifyNoInteractions(holderGuard, userRepository);
    assertThat(events).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(
      value = LockModeType.class,
      names = {"NONE", "OPTIMISTIC", "PESSIMISTIC_READ"})
  void replacingPermissionsOfARoleNotWriteLockedIsRejected(LockModeType lock) {
    Role role = role(Set.of(1L));
    when(entityManager.getLockMode(role)).thenReturn(lock);

    assertThatThrownBy(() -> operations.replaceRolePermissions(role, Set.of(2L)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(role.getPermissionIds()).containsExactly(1L);
    verifyNoInteractions(holderGuard, userRepository);
  }

  private Role locked(Role role) {
    when(entityManager.getLockMode(role)).thenReturn(LockModeType.PESSIMISTIC_WRITE);
    return role;
  }

  private static Role role(Set<Long> permissionIds) {
    Role role = Role.builder().name("検証").systemRole(false).permissionIds(permissionIds).build();
    role.setId(7L);
    return role;
  }

  private User user(UserType type) {
    User user =
        User.builder()
            .userType(type)
            .email(type == UserType.SERVICE ? null : "credential@kizuna.test")
            .password(type == UserType.SERVICE ? null : "hash")
            .enabled(true)
            .roleIds(type.holdsRoles() ? Set.of(1L) : Set.of())
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(1L))
            .build();
    user.setId(42L);
    return user;
  }
}
