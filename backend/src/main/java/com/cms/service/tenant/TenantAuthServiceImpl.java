package com.cms.service.tenant;

import com.cms.config.TenantScoped;
import com.cms.config.interceptor.TenantContext;
import com.cms.model.dto.auth.Token;
import com.cms.model.dto.tenant.TenantRegisterRequest;
import com.cms.model.entity.central.tenant.Tenant;
import com.cms.model.entity.tenant.security.TenantUser;
import com.cms.repository.central.TenantRepository;
import com.cms.repository.tenant.TenantUserRepository;
import com.cms.utils.JwtUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    return jwtUtil.generateToken(Objects.requireNonNull(auth.getName()), "TenantAuth", claims);
  }

  @Override
  @Transactional
  public Tenant register(@NonNull Long tenantId, TenantRegisterRequest tenantRegisterRequest) {
    Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
    TenantUser entity = new TenantUser();
    String email = tenantRegisterRequest.getEmail();
    String nickname =
        (email != null && email.contains("@")) ? email.substring(0, email.indexOf('@')) : "admin";
    entity.setNickname(nickname);
    entity.setTenant(tenant);
    entity.setEmail(email);
    entity.setPassword(passwordEncoder.encode(tenantRegisterRequest.getPassword()));
    userRepository.save(entity);
    return tenant;
  }
}
