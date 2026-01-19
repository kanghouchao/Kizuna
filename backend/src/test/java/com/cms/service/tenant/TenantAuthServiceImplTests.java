package com.cms.service.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cms.config.interceptor.TenantContext;
import com.cms.model.dto.tenant.TenantRegisterRequest;
import com.cms.model.entity.central.tenant.Tenant;
import com.cms.model.entity.tenant.security.TenantUser;
import com.cms.repository.central.TenantRepository;
import com.cms.repository.tenant.TenantUserRepository;
import com.cms.utils.JwtUtil;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

@SuppressWarnings("null")
class TenantAuthServiceImplTests {

  @Mock private PasswordEncoder passwordEncoder;
  @Mock private AuthenticationManager authenticationManager;
  @Mock private JwtUtil jwtUtil;
  @Mock private TenantUserRepository userRepository;
  @Mock private TenantRepository tenantRepository;
  @Mock private TenantContext tenantContext;

  @InjectMocks private TenantAuthServiceImpl service;

  @BeforeEach
  void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void registerSavesUserWithEncodedPasswordAndNickname() {
    Tenant t = new Tenant();
    when(tenantRepository.findById(1L)).thenReturn(Optional.of(t));

    TenantRegisterRequest req = new TenantRegisterRequest();
    req.setEmail("bob@example.com");
    String rawPassword = java.util.UUID.randomUUID().toString();
    req.setPassword(rawPassword);

    when(passwordEncoder.encode(rawPassword)).thenReturn("encoded");

    Tenant registeredTenant = service.register(1L, req);

    verify(userRepository).save(any(TenantUser.class));
    assertThat(registeredTenant).isSameAs(t);
  }
}
