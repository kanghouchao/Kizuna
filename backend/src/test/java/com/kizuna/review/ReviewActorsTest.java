package com.kizuna.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.review.application.ReviewActors;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

class ReviewActorsTest {
  private final UserRepository users = mock(UserRepository.class);
  private final RoleRepository roles = mock(RoleRepository.class);
  private final User user = mock(User.class);
  private final ReviewActors actors = new ReviewActors(users, roles);

  @BeforeEach
  void setup() {
    when(users.findByEmail("actor")).thenReturn(Optional.of(user));
    when(user.getId()).thenReturn(1L);
    when(user.getEmail()).thenReturn("actor");
    when(user.getDisplayName()).thenReturn("担当者");
    when(user.getUserType()).thenReturn(UserType.STAFF);
    when(user.getEnabled()).thenReturn(true);
    when(user.getRoleIds()).thenReturn(Set.of(3L));
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void ordinaryAccessRequiresCurrentStoreViewAndOperationGrants() {
    denied();
    when(user.authorizes(2L)).thenReturn(true);
    denied();
    when(roles.findIdsByPermissionCode("REVIEW_VIEW")).thenReturn(Set.of(3L));
    denied();
    when(roles.findIdsByPermissionCode("REVIEW_MANAGE")).thenReturn(Set.of(3L));
    assertThat(actors.require(2L, "actor", PermissionCode.REVIEW_MANAGE).id()).isEqualTo(1L);
    assertThatThrownBy(() -> actors.require(3L, "actor", PermissionCode.REVIEW_MANAGE))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void missingStoreIsDeniedEvenWithEveryGrant() {
    when(user.authorizes(null)).thenReturn(true);
    when(roles.findIdsByPermissionCode("REVIEW_VIEW")).thenReturn(Set.of(3L));
    when(roles.findIdsByPermissionCode("REVIEW_MANAGE")).thenReturn(Set.of(3L));
    assertThatThrownBy(() -> actors.require(null, "actor", PermissionCode.REVIEW_MANAGE))
        .isInstanceOf(AccessDeniedException.class);
  }

  private void denied() {
    assertThatThrownBy(() -> actors.require(2L, "actor", PermissionCode.REVIEW_MANAGE))
        .isInstanceOf(AccessDeniedException.class);
  }
}
