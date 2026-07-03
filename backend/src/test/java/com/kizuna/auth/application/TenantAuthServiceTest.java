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

import com.kizuna.auth.api.dto.TenantRegisterRequest;
import com.kizuna.auth.api.dto.Token;
import com.kizuna.auth.infrastructure.JwtUtil;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.tenancy.TenantContext;
import com.kizuna.tenant.domain.Tenant;
import com.kizuna.tenant.domain.TenantRepository;
import com.kizuna.user.domain.StoreUser;
import com.kizuna.user.domain.StoreUserRepository;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class TenantAuthServiceTest {

  @Mock private StoreUserRepository userRepository;
  @Mock private TenantRepository tenantRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private AuthenticationManager authenticationManager;
  @Mock private JwtUtil jwtUtil;
  @Mock private TenantContext tenantContext;
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private AppProperties appProperties;
  @Mock private ValueOperations<String, String> valueOperations;

  @InjectMocks private TenantAuthService authService;

  @Captor private ArgumentCaptor<Map<String, Object>> claimsCaptor;

  @BeforeEach
  void setUp() {}

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

    verify(jwtUtil).generateToken(eq("user@example.com"), eq("TenantAuth"), claimsCaptor.capture());
    assertThat(claimsCaptor.getValue()).containsKey("authorities");
    assertThat(claimsCaptor.getValue()).containsEntry("tenantId", 1L);
  }

  @Test
  void login_authFailed_throwsException() {
    when(authenticationManager.authenticate(any()))
        .thenThrow(new RuntimeException("Bad credentials"));
    assertThatThrownBy(() -> authService.login("bad", "pass")).isInstanceOf(RuntimeException.class);
  }

  @Test
  void initializeAdminUser_success_createsUserAndDeletesToken() {
    TenantRegisterRequest req = new TenantRegisterRequest();
    req.setToken("12345678901234567890123456789012");
    req.setEmail("admin@test.com");
    req.setPassword("password123");

    Tenant tenant = new Tenant();
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(appProperties.getTenantCreatorCachePerfix()).thenReturn("tenant-creator:");
    when(valueOperations.get("tenant-creator:" + req.getToken())).thenReturn("1");
    when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
    when(passwordEncoder.encode("password123")).thenReturn("encoded-password");

    Tenant result = authService.initializeAdminUser(req);

    assertThat(result).isSameAs(tenant);
    verify(userRepository).save(any());
    verify(redisTemplate).delete("tenant-creator:" + req.getToken());
  }

  @Test
  void initializeAdminUser_invalidToken_throwsIllegalArgumentException() {
    TenantRegisterRequest req = new TenantRegisterRequest();
    req.setToken("12345678901234567890123456789012");
    req.setEmail("admin@test.com");
    req.setPassword("password123");

    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(appProperties.getTenantCreatorCachePerfix()).thenReturn("tenant-creator:");
    when(valueOperations.get("tenant-creator:" + req.getToken())).thenReturn(null);

    assertThatThrownBy(() -> authService.initializeAdminUser(req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid or expired registration token");
  }

  @Test
  void initializeAdminUser_tenantNotFound_throwsNoSuchElementException() {
    TenantRegisterRequest req = new TenantRegisterRequest();
    req.setToken("12345678901234567890123456789012");
    req.setEmail("admin@test.com");
    req.setPassword("password123");

    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(appProperties.getTenantCreatorCachePerfix()).thenReturn("tenant-creator:");
    when(valueOperations.get("tenant-creator:" + req.getToken())).thenReturn("999");
    when(tenantRepository.findById(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.initializeAdminUser(req))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void changePassword_success_savesEncodedPassword() {
    StoreUser user = new StoreUser();
    user.setEmail("staff@store1.kizuna.com");
    user.setPassword("test-placeholder-stored-hash");

    when(userRepository.findByEmail("staff@store1.kizuna.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("current", "test-placeholder-stored-hash")).thenReturn(true);
    when(passwordEncoder.encode("newpass123")).thenReturn("test-placeholder-encoded-hash");

    authService.changePassword("staff@store1.kizuna.com", "current", "newpass123");

    assertThat(user.getPassword()).isEqualTo("test-placeholder-encoded-hash");
    verify(userRepository).save(user);
  }

  @Test
  void changePassword_wrongCurrentPassword_throws() {
    StoreUser user = new StoreUser();
    user.setPassword("test-placeholder-stored-hash");

    when(userRepository.findByEmail("staff@store1.kizuna.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("wrong", "test-placeholder-stored-hash")).thenReturn(false);

    assertThatThrownBy(
            () -> authService.changePassword("staff@store1.kizuna.com", "wrong", "newpass123"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("現在のパスワードが正しくありません");
    verify(userRepository, never()).save(any());
  }

  @Test
  void changePassword_userNotFound_throws() {
    when(userRepository.findByEmail("missing@x.com")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.changePassword("missing@x.com", "current", "newpass123"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("ユーザーが見つかりません");
  }
}
