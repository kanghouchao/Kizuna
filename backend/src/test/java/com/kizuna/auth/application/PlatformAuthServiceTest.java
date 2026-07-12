package com.kizuna.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.kizuna.auth.api.dto.Token;
import com.kizuna.auth.infrastructure.JwtUtil;
import com.kizuna.user.domain.PlatformRole;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PlatformAuthServiceTest {

  @Mock private PlatformUserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtUtil jwtUtil;

  @Captor private ArgumentCaptor<Map<String, Object>> claimsCaptor;

  @InjectMocks private PlatformAuthService authService;

  private PlatformUser hqAdmin() {
    return PlatformUser.builder()
        .email("admin@kizuna.test")
        .password("stored-hash")
        .displayName("HQ管理者")
        .enabled(true)
        .role(PlatformRole.HQ_ADMIN)
        .storeScopeType(StoreScopeType.ALL_STORES)
        .storeIds(Set.of())
        .build();
  }

  @Test
  void login_success_issuesPlatformTokenWithRoleAndScopeClaims() {
    when(userRepository.findByEmail("admin@kizuna.test")).thenReturn(Optional.of(hqAdmin()));
    when(passwordEncoder.matches("pass", "stored-hash")).thenReturn(true);
    Token mockToken = new Token("platform_token", 12345L);
    when(jwtUtil.generateToken(eq("admin@kizuna.test"), eq(JwtUtil.ISSUER_PLATFORM), any()))
        .thenReturn(mockToken);

    Token res = authService.login("admin@kizuna.test", "pass");

    assertThat(res.token()).isEqualTo("platform_token");
    org.mockito.Mockito.verify(jwtUtil)
        .generateToken(
            eq("admin@kizuna.test"), eq(JwtUtil.ISSUER_PLATFORM), claimsCaptor.capture());
    Map<String, Object> claims = claimsCaptor.getValue();
    assertThat(claims.get("authorities")).isEqualTo(List.of("ROLE_HQ_ADMIN"));
    assertThat(claims.get("role")).isEqualTo("HQ_ADMIN");
    assertThat(claims.get("storeScopeType")).isEqualTo("ALL_STORES");
    assertThat(claims.get("storeIds")).isEqualTo(List.of());
  }

  @Test
  void login_success_specificStores_carriesStoreIdsClaim() {
    PlatformUser manager =
        PlatformUser.builder()
            .email("mgr@kizuna.test")
            .password("stored-hash")
            .displayName("店長")
            .enabled(true)
            .role(PlatformRole.STORE_MANAGER)
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(1L))
            .build();
    when(userRepository.findByEmail("mgr@kizuna.test")).thenReturn(Optional.of(manager));
    when(passwordEncoder.matches("pass", "stored-hash")).thenReturn(true);
    when(jwtUtil.generateToken(eq("mgr@kizuna.test"), eq(JwtUtil.ISSUER_PLATFORM), any()))
        .thenReturn(new Token("t", 1L));

    authService.login("mgr@kizuna.test", "pass");

    org.mockito.Mockito.verify(jwtUtil)
        .generateToken(eq("mgr@kizuna.test"), eq(JwtUtil.ISSUER_PLATFORM), claimsCaptor.capture());
    Map<String, Object> claims = claimsCaptor.getValue();
    assertThat(claims.get("role")).isEqualTo("STORE_MANAGER");
    assertThat(claims.get("storeScopeType")).isEqualTo("SPECIFIC_STORES");
    assertThat(claims.get("storeIds")).isEqualTo(List.of(1L));
  }

  @Test
  void login_emailNotFound_throwsBadCredentials() {
    when(userRepository.findByEmail("missing@kizuna.test")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.login("missing@kizuna.test", "pass"))
        .isInstanceOf(BadCredentialsException.class);
  }

  @Test
  void login_wrongPassword_throwsBadCredentials() {
    when(userRepository.findByEmail("admin@kizuna.test")).thenReturn(Optional.of(hqAdmin()));
    when(passwordEncoder.matches("wrong", "stored-hash")).thenReturn(false);

    assertThatThrownBy(() -> authService.login("admin@kizuna.test", "wrong"))
        .isInstanceOf(BadCredentialsException.class);
  }

  @Test
  void login_disabledUser_throwsDisabled() {
    PlatformUser disabled =
        PlatformUser.builder()
            .email("admin@kizuna.test")
            .password("stored-hash")
            .displayName("HQ管理者")
            .enabled(false)
            .role(PlatformRole.HQ_ADMIN)
            .storeScopeType(StoreScopeType.ALL_STORES)
            .storeIds(Set.of())
            .build();
    when(userRepository.findByEmail("admin@kizuna.test")).thenReturn(Optional.of(disabled));
    when(passwordEncoder.matches("pass", "stored-hash")).thenReturn(true);

    assertThatThrownBy(() -> authService.login("admin@kizuna.test", "pass"))
        .isInstanceOf(DisabledException.class);
  }
}
