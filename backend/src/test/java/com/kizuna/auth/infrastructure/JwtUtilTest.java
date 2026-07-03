package com.kizuna.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.auth.api.dto.Token;
import com.kizuna.shared.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JwtUtilTest {

  private final String secret = "mysecretmysecretmysecretmysecretmysecretmysecretmysecretmysecret";

  @Test
  void generateAndValidateToken() {
    JwtUtil jwtUtil = createJwtUtil(secret, 3600000L);

    Token token = jwtUtil.generateToken("user", "issuer", Map.of("role", "ADMIN"));
    assertThat(token.token()).isNotNull();

    Claims claims = jwtUtil.getClaims(token.token());
    assertThat(claims.getSubject()).isEqualTo("user");
    assertThat(claims.getIssuer()).isEqualTo("issuer");
    assertThat(claims.get("role")).isEqualTo("ADMIN");
  }

  @Test
  void getClaims_throwsWhenTokenExpired() {
    JwtUtil expiredJwtUtil = createJwtUtil(secret, -1000L);

    Token token = expiredJwtUtil.generateToken("user", "issuer", Map.of());

    assertThatThrownBy(() -> expiredJwtUtil.getClaims(token.token()))
        .isInstanceOf(ExpiredJwtException.class);
  }

  @Test
  void getClaims_throwsWhenSignatureInvalid() {
    JwtUtil jwtUtil = createJwtUtil(secret, 3600000L);
    Token token = jwtUtil.generateToken("user", "issuer", Map.of("role", "ADMIN"));

    JwtUtil otherJwtUtil =
        createJwtUtil(
            "anothersecretanothersecretanothersecretanothersecretanothersecret1234", 3600000L);

    assertThatThrownBy(() -> otherJwtUtil.getClaims(token.token()))
        .isInstanceOf(JwtException.class);
  }

  @Test
  void getClaims_throwsWhenTokenMalformed() {
    JwtUtil jwtUtil = createJwtUtil(secret, 3600000L);

    assertThatThrownBy(() -> jwtUtil.getClaims("malformed.token")).isInstanceOf(JwtException.class);
  }

  private JwtUtil createJwtUtil(String jwtSecret, long expiration) {
    AppProperties properties = new AppProperties();
    AppProperties.Jwt jwt = new AppProperties.Jwt();
    jwt.setSecret(jwtSecret);
    jwt.setExpiration(expiration);
    properties.setJwt(jwt);
    return new JwtUtil(properties);
  }
}
