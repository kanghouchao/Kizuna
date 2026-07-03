package com.kizuna.auth.application;

import com.kizuna.auth.api.dto.TenantRegisterRequest;
import com.kizuna.auth.api.dto.Token;
import com.kizuna.auth.infrastructure.JwtUtil;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.tenancy.TenantContext;
import com.kizuna.shared.tenancy.TenantScoped;
import com.kizuna.tenant.domain.Tenant;
import com.kizuna.tenant.domain.TenantRepository;
import com.kizuna.user.domain.StoreUser;
import com.kizuna.user.domain.StoreUserRepository;
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
public class TenantAuthService {

  private final PasswordEncoder passwordEncoder;
  private final AuthenticationManager authenticationManager;
  private final JwtUtil jwtUtil;
  private final StoreUserRepository userRepository;
  private final TenantRepository tenantRepository;
  private final TenantContext tenantContext;
  private final StringRedisTemplate redisTemplate;
  private final AppProperties appProperties;

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
    StoreUser entity = new StoreUser();
    String email = tenantRegisterRequest.getEmail();
    String nickname =
        (email != null && email.contains("@")) ? email.substring(0, email.indexOf('@')) : "admin";
    entity.setNickname(nickname);
    entity.setTenantId(tenant.getId());
    entity.setEmail(email);
    entity.setPassword(passwordEncoder.encode(tenantRegisterRequest.getPassword()));
    userRepository.save(entity);
    redisTemplate.delete(
        appProperties.getTenantCreatorCachePerfix() + tenantRegisterRequest.getToken());
    return tenant;
  }

  @Transactional
  @TenantScoped
  public void changePassword(String email, String currentPassword, String newPassword) {
    StoreUser user =
        userRepository.findByEmail(email).orElseThrow(() -> new ServiceException("ユーザーが見つかりません"));
    if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
      throw new ServiceException("現在のパスワードが正しくありません");
    }
    user.changePassword(passwordEncoder.encode(newPassword));
    userRepository.save(user);
  }
}
