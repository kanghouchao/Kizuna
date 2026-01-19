package com.kizuna.controller.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.kizuna.model.dto.auth.LoginRequest;
import com.kizuna.model.dto.auth.Token;
import com.kizuna.service.tenant.TenantAuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class TenantAuthControllerTest {

  @Mock TenantAuthService authService;

  @InjectMocks TenantAuthController controller;

  @Test
  void login_returnsToken() {
    LoginRequest req = new LoginRequest();
    req.setUsername("user");
    req.setPassword("pass");
    Token token = new Token("access", 3600L);
    when(authService.login("user", "pass")).thenReturn(token);

    ResponseEntity<?> response = controller.login(req);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(token);
  }
}
