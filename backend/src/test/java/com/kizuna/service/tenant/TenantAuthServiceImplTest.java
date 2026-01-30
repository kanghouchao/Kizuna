package com.kizuna.service.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.config.interceptor.TenantContext;
import com.kizuna.model.dto.auth.Token;
import com.kizuna.repository.central.TenantRepository;
import com.kizuna.repository.tenant.TenantUserRepository;
import com.kizuna.utils.JwtUtil;
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
class TenantAuthServiceImplTest {

  @Mock private TenantUserRepository userRepository;
  @Mock private TenantRepository tenantRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private AuthenticationManager authenticationManager;
  @Mock private JwtUtil jwtUtil;
  @Mock private TenantContext tenantContext;

  @InjectMocks private TenantAuthServiceImpl authService;

  @Test
  void login_success_returnsToken() {
    Authentication auth = mock(Authentication.class);
    when(auth.getName()).thenReturn("user@example.com");
    when(auth.getAuthorities()).thenAnswer(i -> List.of());

    when(authenticationManager.authenticate(any())).thenReturn(auth);
    when(tenantContext.getTenantId()).thenReturn(1L);

    Token mockToken = new Token("token123", 12345L);
    when(jwtUtil.generateToken(eq("user@example.com"), eq("TenantAuth"), anyMap()))
        .thenReturn(mockToken);

    Token res = authService.login("user@example.com", "pass");
    assertThat(res.token()).isEqualTo("token123");
  }

  @Test
  void login_authFailed_throwsException() {
    when(authenticationManager.authenticate(any()))
        .thenThrow(new RuntimeException("Bad credentials"));
    assertThatThrownBy(() -> authService.login("bad", "pass")).isInstanceOf(RuntimeException.class);
  }

  @Test
  void register_createsUser() {
    com.kizuna.model.dto.tenant.TenantRegisterRequest req =
        new com.kizuna.model.dto.tenant.TenantRegisterRequest();
    req.setEmail("admin@test.com");
    req.setPassword("pass");

    com.kizuna.model.entity.central.tenant.Tenant tenant =
        new com.kizuna.model.entity.central.tenant.Tenant();
    when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
    when(passwordEncoder.encode("pass")).thenReturn("encoded");

    authService.register(1L, req);

    verify(userRepository).save(any());
  }
}
