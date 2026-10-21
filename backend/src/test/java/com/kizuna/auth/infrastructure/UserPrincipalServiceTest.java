package com.kizuna.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class UserPrincipalServiceTest {

  @Mock private UserRepository userRepository;

  @InjectMocks private UserPrincipalService service;

  @Test
  void loadUserByUsername_existingEmail_returnsUserDetailsWrappingUser() {
    User user =
        User.builder()
            .email("admin@kizuna.test")
            .password("stored-hash")
            .displayName("HQ管理者")
            .enabled(true)
            .userType(UserType.PLATFORM_USER)
            .roleIds(Set.of(10L))
            .storeScopeType(StoreScopeType.ALL_STORES)
            .storeIds(Set.of())
            .build();
    when(userRepository.findByEmail("admin@kizuna.test")).thenReturn(Optional.of(user));

    UserDetails result = service.loadUserByUsername("admin@kizuna.test");

    assertThat(result.getUsername()).isEqualTo("admin@kizuna.test");
    assertThat(result.getPassword()).isEqualTo("stored-hash");
    assertThat(result.isEnabled()).isTrue();
    assertThat(((UserPrincipal) result).getUser()).isSameAs(user);
  }

  @Test
  void loadUserByUsername_missingEmail_throwsUsernameNotFoundException() {
    when(userRepository.findByEmail("missing@kizuna.test")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.loadUserByUsername("missing@kizuna.test"))
        .isInstanceOf(UsernameNotFoundException.class);
  }
}
