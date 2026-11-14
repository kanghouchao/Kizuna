package com.kizuna.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActorIdentityServiceTest {

  @Mock private UserRepository userRepository;
  @InjectMocks private ActorIdentityService service;

  @Test
  void resolvesAuthenticatedEmailToUserId() {
    User user = mock(User.class);
    when(user.getId()).thenReturn(42L);
    when(userRepository.findByEmail("actor@kizuna.test")).thenReturn(Optional.of(user));

    assertThat(service.requireUserId("actor@kizuna.test")).isEqualTo(42L);
  }

  @Test
  void resolvesTheActorWithTheTypeAndNameOfTheUserRow() {
    User user = mock(User.class);
    when(user.getId()).thenReturn(42L);
    when(user.getUserType()).thenReturn(UserType.SERVICE);
    when(user.getDisplayName()).thenReturn("実行主体");
    when(userRepository.findByEmail("actor@kizuna.test")).thenReturn(Optional.of(user));

    assertThat(service.requireActor("actor@kizuna.test"))
        .isEqualTo(new AuditActor(42L, "SERVICE", "実行主体"));
  }

  @Test
  void rejectsMissingActor() {
    when(userRepository.findByEmail("missing@kizuna.test")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.requireActor("missing@kizuna.test"))
        .isInstanceOf(StaleSessionException.class)
        .hasMessage("認証セッションの主体が存在しません");
  }

  @Test
  void rejectsMissingPrincipal() {
    when(userRepository.findByEmail("missing@kizuna.test")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.requireUserId("missing@kizuna.test"))
        .isInstanceOf(StaleSessionException.class)
        .hasMessage("認証セッションの主体が存在しません");
  }
}
