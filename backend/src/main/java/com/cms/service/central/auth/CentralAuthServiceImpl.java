package com.cms.service.central.auth;

import com.cms.model.dto.auth.Token;
import com.cms.utils.JwtUtil;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CentralAuthServiceImpl implements CentralAuthService {

  private final AuthenticationManager authenticationManager;
  private final JwtUtil jwtUtil;

  @Override
  public Token login(String username, String password) {
    Authentication auth =
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(username, password));

    return jwtUtil.generateToken(
        Objects.requireNonNull(auth.getName()),
        "CentralAuth",
        Map.of("authorities", auth.getAuthorities().stream().map(a -> a.getAuthority()).toList()));
  }
}
