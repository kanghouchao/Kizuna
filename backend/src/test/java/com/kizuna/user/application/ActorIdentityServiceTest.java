package com.kizuna.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActorIdentityServiceTest {

  @Mock private PlatformUserRepository platformUserRepository;
  @InjectMocks private ActorIdentityService service;

  @Test
  void resolvesAuthenticatedEmailToUserId() {
    PlatformUser user = mock(PlatformUser.class);
    when(user.getId()).thenReturn(42L);
    when(platformUserRepository.findByEmail("actor@kizuna.test")).thenReturn(Optional.of(user));

    assertThat(service.requireUserId("actor@kizuna.test")).isEqualTo(42L);
  }

  @Test
  void rejectsMissingPrincipal() {
    when(platformUserRepository.findByEmail("missing@kizuna.test")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.requireUserId("missing@kizuna.test"))
        .isInstanceOf(StaleSessionException.class)
        .hasMessage("認証セッションの主体が存在しません");
  }
}
