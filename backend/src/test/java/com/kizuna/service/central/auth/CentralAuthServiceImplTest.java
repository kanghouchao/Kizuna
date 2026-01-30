package com.kizuna.service.central.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.model.dto.auth.Token;
import com.kizuna.utils.JwtUtil;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class CentralAuthServiceImplTest {

  @Mock private AuthenticationManager authenticationManager;
  @Mock private JwtUtil jwtUtil;

  @InjectMocks private CentralAuthServiceImpl authService;

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
}
