package com.kizuna.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.auth.api.dto.Token;
import com.kizuna.auth.infrastructure.JwtUtil;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.user.domain.CentralUser;
import com.kizuna.user.domain.CentralUserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class CentralAuthServiceTest {

  @Mock private AuthenticationManager authenticationManager;
  @Mock private JwtUtil jwtUtil;
  @Mock private CentralUserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;

  @InjectMocks private CentralAuthService authService;

  @Test
  void login_success_returnsToken() {
    Authentication auth = mock(Authentication.class);
    when(auth.getName()).thenReturn("admin");
    when(auth.getAuthorities()).thenAnswer(i -> List.of());

    when(authenticationManager.authenticate(any())).thenReturn(auth);

    Token mockToken = new Token("central_token", 12345L);
    when(jwtUtil.generateToken(eq("admin"), eq("CentralAuth"), anyMap())).thenReturn(mockToken);

    Token res = authService.login("admin", "pass");
    assertThat(res.token()).isEqualTo("central_token");
  }

  @Test
  void login_failure_throwsException() {
    when(authenticationManager.authenticate(any())).thenThrow(new RuntimeException("Bad"));
    assertThatThrownBy(() -> authService.login("bad", "pass")).isInstanceOf(RuntimeException.class);
  }

  @Test
  void changePassword_success_savesEncodedPassword() {
    CentralUser user = new CentralUser();
    user.setUsername("admin");
    user.setPassword("test-placeholder-stored-hash");

    when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("current", "test-placeholder-stored-hash")).thenReturn(true);
    when(passwordEncoder.encode("newpass123")).thenReturn("test-placeholder-encoded-hash");

    authService.changePassword("admin", "current", "newpass123");

    assertThat(user.getPassword()).isEqualTo("test-placeholder-encoded-hash");
    verify(userRepository).save(user);
  }

  @Test
  void changePassword_wrongCurrentPassword_throws() {
    CentralUser user = new CentralUser();
    user.setPassword("test-placeholder-stored-hash");

    when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("wrong", "test-placeholder-stored-hash")).thenReturn(false);

    assertThatThrownBy(() -> authService.changePassword("admin", "wrong", "newpass123"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("現在のパスワードが正しくありません");
    verify(userRepository, never()).save(any());
  }

  @Test
  void changePassword_userNotFound_throws() {
    when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.changePassword("missing", "current", "newpass123"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("ユーザーが見つかりません");
  }
}
