package com.kizuna.service.tenant.auth;

import com.kizuna.model.dto.auth.Token;
import com.kizuna.model.dto.tenant.TenantRegisterRequest;
import com.kizuna.model.entity.central.tenant.Tenant;
import com.kizuna.model.entity.tenant.security.TenantUser;
import com.kizuna.repository.central.TenantRepository;
import com.kizuna.repository.tenant.TenantUserRepository;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.tenancy.TenantContext;
import com.kizuna.shared.tenancy.TenantScoped;
import com.kizuna.utils.JwtUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Log4j2
@RequiredArgsConstructor
public class TenantAuthServiceImpl implements TenantAuthService {

  private final PasswordEncoder passwordEncoder;
  private final AuthenticationManager authenticationManager;
  private final JwtUtil jwtUtil;
  private final TenantUserRepository userRepository;
  private final TenantRepository tenantRepository;
  private final TenantContext tenantContext;
  private final StringRedisTemplate redisTemplate;
  private final AppProperties appProperties;

  @Override
  @Transactional(readOnly = true)
  @TenantScoped
  public Token login(String username, String password) {
    Authentication auth =
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(username, password));
    Long tenantId = tenantContext.getTenantId();
    Map<String, Object> claims =
        new HashMap<>(
            Map.of(
                "authorities",
                auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList()));
    if (tenantId != null) {
      claims.put("tenantId", tenantId);
    }
    return jwtUtil.generateToken(
        Objects.requireNonNull(auth.getName()), JwtUtil.ISSUER_TENANT, claims);
  }

  @Override
  @Transactional
  public Tenant initializeAdminUser(TenantRegisterRequest tenantRegisterRequest) {
    String tenantId =
        redisTemplate
            .opsForValue()
            .get(appProperties.getTenantCreatorCachePerfix() + tenantRegisterRequest.getToken());
    if (!StringUtils.hasText(tenantId)) {
      throw new IllegalArgumentException("Invalid or expired registration token");
    }
    Tenant tenant = tenantRepository.findById(Long.parseLong(tenantId)).orElseThrow();
    TenantUser entity = new TenantUser();
    String email = tenantRegisterRequest.getEmail();
    String nickname =
        (email != null && email.contains("@")) ? email.substring(0, email.indexOf('@')) : "admin";
    entity.setNickname(nickname);
    entity.setTenant(tenant);
    entity.setEmail(email);
    entity.setPassword(passwordEncoder.encode(tenantRegisterRequest.getPassword()));
    userRepository.save(entity);
    redisTemplate.delete(
        appProperties.getTenantCreatorCachePerfix() + tenantRegisterRequest.getToken());
    return tenant;
  }
}
