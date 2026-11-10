package com.kizuna.notificationdelivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.notificationdelivery.application.NotificationActors;
import com.kizuna.notificationdelivery.application.NotificationActors.Action;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class NotificationActorsTest {
  private final UserRepository users = mock(UserRepository.class);
  private final RoleRepository roles = mock(RoleRepository.class);
  private final User user = mock(User.class);
  private final NotificationActors actors = new NotificationActors(users, roles);

  @BeforeEach
  void setup() {
    when(users.findByEmail("actor")).thenReturn(Optional.of(user));
    when(user.getId()).thenReturn(1L);
    when(user.getDisplayName()).thenReturn("担当者");
    when(user.getUserType()).thenReturn(UserType.STAFF);
    when(user.getEnabled()).thenReturn(true);
    when(user.getRoleIds()).thenReturn(Set.of(3L));
    when(roles.findIdsByPermissionCode("NOTIFICATION_VIEW")).thenReturn(Set.of(3L));
    when(roles.findIdsByPermissionCode("NOTIFICATION_MANAGE")).thenReturn(Set.of(3L));
  }

  @Test
  void onlyTheGivenAuthorizedStorePasses() {
    when(user.authorizes(7L)).thenReturn(true);
    assertThat(actors.require(7L, "actor", Action.MANAGE).id()).isEqualTo(1L);
    assertThatThrownBy(() -> actors.require(8L, "actor", Action.MANAGE))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void memberRowsAreJudgedByTheMemberPermissionsWithoutAnyStoreSet() {
    when(roles.findIdsByPermissionCode("MEMBER_NOTIFICATION_VIEW")).thenReturn(Set.of(3L));
    when(roles.findIdsByPermissionCode("MEMBER_NOTIFICATION_MANAGE")).thenReturn(Set.of(3L));
    assertThat(actors.require(null, "actor", Action.MANAGE).id()).isEqualTo(1L);
    verify(user, never()).authorizes(any());
  }

  @Test
  void storeNotificationPermissionsDoNotOpenMemberRows() {
    assertThatThrownBy(() -> actors.require(null, "actor", Action.VIEW))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void memberRowsStillNeedTheActionPermission() {
    when(roles.findIdsByPermissionCode("MEMBER_NOTIFICATION_VIEW")).thenReturn(Set.of(3L));
    assertThatThrownBy(() -> actors.require(null, "actor", Action.SEND))
        .isInstanceOf(AccessDeniedException.class);
  }
}
