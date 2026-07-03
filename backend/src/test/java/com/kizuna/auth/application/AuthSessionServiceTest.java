package com.kizuna.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.kizuna.auth.infrastructure.TokenBlacklistService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AuthSessionServiceTest {

  @Mock private TokenBlacklistService tokenBlacklistService;

  @InjectMocks private AuthSessionService service;

  @Test
  void invalidate_blacklistsTokenAndClearsContext() {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("user", "n/a"));

    service.invalidate("Bearer some-token");

    verify(tokenBlacklistService).blacklist("Bearer some-token");
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }
}
