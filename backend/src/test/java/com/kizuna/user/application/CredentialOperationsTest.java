package com.kizuna.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.user.domain.InvalidCredentialAssignmentException;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserCredentialsChanged;
import com.kizuna.user.domain.UserType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CredentialOperationsTest {
  private final List<Object> events = new ArrayList<>();
  private final CredentialOperations operations = new CredentialOperations(events::add);

  @ParameterizedTest
  @EnumSource(
      value = UserType.class,
      names = {"PLATFORM_USER", "CAST", "MEMBER"})
  void repeatedStopNotifiesTheSameVersion(UserType type) {
    User user = user(type);
    operations.stop(user);
    operations.stop(user);
    assertThat(user.getEnabled()).isFalse();
    assertThat(user.getCredentialVersion()).isEqualTo(1L);
    assertThat(events)
        .containsExactly(
            new UserCredentialsChanged(user.getEmail(), 1L),
            new UserCredentialsChanged(user.getEmail(), 1L));
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
    User user = user(UserType.PLATFORM_USER);
    operations.changePassword(user, "encoded");
    assertThat(user.getPassword()).isEqualTo("encoded");
    operations.invalidateSessions(user);
    assertThat(user.getCredentialVersion()).isEqualTo(2L);
    assertThat(events)
        .containsExactly(
            new UserCredentialsChanged(user.getEmail(), 1L),
            new UserCredentialsChanged(user.getEmail(), 2L));
  }

  @Test
  void serviceCannotReceivePassword() {
    User user = user(UserType.SERVICE);
    assertThatThrownBy(() -> operations.changePassword(user, "encoded"))
        .isInstanceOf(InvalidCredentialAssignmentException.class);
    assertThat(user.getCredentialVersion()).isZero();
    assertThat(events).isEmpty();
  }

  private User user(UserType type) {
    return User.builder()
        .userType(type)
        .email(type == UserType.SERVICE ? null : "credential@kizuna.test")
        .password(type == UserType.SERVICE ? null : "hash")
        .enabled(true)
        .roleIds(type.holdsRoles() ? Set.of(1L) : Set.of())
        .build();
  }
}
