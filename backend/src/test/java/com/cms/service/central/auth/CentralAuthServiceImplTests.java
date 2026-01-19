package com.cms.service.central.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.cms.model.dto.auth.Token;
import com.cms.utils.JwtUtil;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class CentralAuthServiceImplTests {

  @Mock private AuthenticationManager authenticationManager;
  @Mock private JwtUtil jwtUtil;

  @InjectMocks private CentralAuthServiceImpl service;

  @BeforeEach
  void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void loginReturnsTokenWithAuthorities() {
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            "alice", "pw", List.of(new SimpleGrantedAuthority("ROLE_USER")));

    when(authenticationManager.authenticate(new UsernamePasswordAuthenticationToken("alice", "pw")))
        .thenReturn(auth);

    Token expected = new Token("tkn", System.currentTimeMillis() + 10000);
    when(jwtUtil.generateToken("alice", "CentralAuth", Map.of("authorities", List.of("ROLE_USER"))))
        .thenReturn(expected);

    Token actual = service.login("alice", "pw");
    assertThat(actual.token()).isEqualTo(expected.token());
  }
}
