package com.kizuna.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.survey.application.SurveyActors;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class SurveyActorsTest {
  private final UserRepository users = mock(UserRepository.class);
  private final RoleRepository roles = mock(RoleRepository.class);
  private final User user = mock(User.class);
  private final SurveyActors actors = new SurveyActors(users, roles);

  @BeforeEach
  void setup() {
    when(users.findByEmail("actor")).thenReturn(Optional.of(user));
    when(user.getId()).thenReturn(1L);
    when(user.getDisplayName()).thenReturn("担当者");
    when(user.getUserType()).thenReturn(UserType.STAFF);
    when(user.getEnabled()).thenReturn(true);
    when(user.getRoleIds()).thenReturn(Set.of(3L));
    when(roles.findIdsByPermissionCode("SURVEY_VIEW")).thenReturn(Set.of(3L));
    when(roles.findIdsByPermissionCode("SURVEY_RECORD")).thenReturn(Set.of(3L));
  }

  @Test
  void grantsOnlyForTheNamedStoreTheUserIsAuthorizedFor() {
    when(user.authorizes(2L)).thenReturn(true);
    assertThat(actors.require(2L, "actor", PermissionCode.SURVEY_RECORD).id()).isEqualTo(1L);
    assertThatThrownBy(() -> actors.require(3L, "actor", PermissionCode.SURVEY_RECORD))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void missingStoreIsDeniedEvenWithEveryGrant() {
    when(user.authorizes(null)).thenReturn(true);
    assertThatThrownBy(() -> actors.require(null, "actor", PermissionCode.SURVEY_RECORD))
        .isInstanceOf(AccessDeniedException.class);
  }
}
