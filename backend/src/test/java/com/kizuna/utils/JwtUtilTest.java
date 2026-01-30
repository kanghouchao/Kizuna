package com.kizuna.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.kizuna.config.AppProperties;
import com.kizuna.model.dto.auth.Token;
import io.jsonwebtoken.Claims;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JwtUtilTest {

  @Mock private AppProperties appProperties;
  private JwtUtil jwtUtil;

  private final String secret = "mysecretmysecretmysecretmysecretmysecretmysecretmysecretmysecret";

  @BeforeEach
  void setUp() {
    when(appProperties.getJwtSecret()).thenReturn(secret);
    when(appProperties.getJwtExpiration()).thenReturn(3600000L);
    jwtUtil = new JwtUtil(appProperties);
  }

  @Test
  void generateAndValidateToken() {
    Token token = jwtUtil.generateToken("user", "issuer", Map.of("role", "ADMIN"));
    assertThat(token.token()).isNotNull();

    Claims claims = jwtUtil.getClaims(token.token());
    assertThat(claims.getSubject()).isEqualTo("user");
    assertThat(claims.getIssuer()).isEqualTo("issuer");
    assertThat(claims.get("role")).isEqualTo("ADMIN");
  }
}
