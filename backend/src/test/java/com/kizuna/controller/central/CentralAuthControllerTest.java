package com.kizuna.controller.central;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.model.dto.auth.LoginRequest;
import com.kizuna.model.dto.auth.Token;
import com.kizuna.model.dto.central.AdminDto;
import com.kizuna.model.entity.central.security.CentralUser;
import com.kizuna.repository.central.CentralUserRepository;
import com.kizuna.service.central.auth.CentralAuthService;
import com.kizuna.utils.JwtUtil;
import java.security.Principal;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class CentralAuthControllerTest {

  @Mock CentralAuthService authService;
  @Mock CentralUserRepository userRepository;
  @Mock RedisTemplate<String, Object> redisTemplate;
  @Mock JwtUtil jwtUtil;

  @InjectMocks CentralAuthController controller;

  @Test
  void login_returnsToken() {
    LoginRequest req = new LoginRequest();
    req.setUsername("admin");
    req.setPassword("pass");
    Token token = new Token("access", 3600L);
    when(authService.login("admin", "pass")).thenReturn(token);

    ResponseEntity<Token> response = controller.login(req);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(token);
  }

  @Test
  void me_returnsAdmin() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("admin");
    CentralUser user = new CentralUser();
    user.setId(1L);
    user.setUsername("admin");
    when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

    ResponseEntity<AdminDto> response = controller.me(principal);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    Assertions.assertNotNull(response.getBody());
    assertThat(response.getBody().getUsername()).isEqualTo("admin");
  }

  @Test
  void me_returns401_whenPrincipalNull() {
    ResponseEntity<AdminDto> response = controller.me(null);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void me_returns404_whenUserNotFound() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("unknown");
    when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

    ResponseEntity<AdminDto> response = controller.me(principal);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void logout_deletesToken() {
    String token = "jwt-token";
    String header = "Bearer " + token;

    ResponseEntity<?> response = controller.logout(header);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(redisTemplate).delete("blacklist:tokens:" + token);
  }

  @Test
  void logout_doesNothingNormally_whenNoHeader() {
    ResponseEntity<?> response = controller.logout(null);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verifyNoInteractions(redisTemplate);
  }
}
