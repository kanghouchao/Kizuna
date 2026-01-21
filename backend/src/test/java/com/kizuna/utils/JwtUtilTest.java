package com.kizuna.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.kizuna.config.AppProperties;
import com.kizuna.model.dto.auth.Token;
import io.jsonwebtoken.Claims;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JwtUtilTest {

  @Mock private AppProperties appProperties;

  @InjectMocks private JwtUtil jwtUtil;

  private static final String TEST_SECRET =
      "test-secret-key-must-be-at-least-256-bits-long-for-hs256";
  private static final long TEST_EXPIRATION = 3600000L;

  @BeforeEach
  void setUp() {
    when(appProperties.getJwtSecret()).thenReturn(TEST_SECRET);
    when(appProperties.getJwtExpiration()).thenReturn(TEST_EXPIRATION);
  }

  @Test
  void generateToken_createsValidToken() {
    String subject = "test@example.com";
    String issuer = "TestIssuer";
    Map<String, Object> claims = new HashMap<>();
    claims.put("role", "ADMIN");
    claims.put("userId", 123);

    Token token = jwtUtil.generateToken(subject, issuer, claims);

    assertThat(token).isNotNull();
    assertThat(token.token()).isNotBlank();
    assertThat(token.expiresAt()).isGreaterThan(System.currentTimeMillis());
  }

  @Test
  void generateToken_withNullIssuer_createsValidToken() {
    String subject = "test@example.com";
    Map<String, Object> claims = new HashMap<>();
    claims.put("role", "USER");

    Token token = jwtUtil.generateToken(subject, null, claims);

    assertThat(token).isNotNull();
    assertThat(token.token()).isNotBlank();
  }

  @Test
  void generateToken_withEmptyClaims_createsValidToken() {
    String subject = "test@example.com";
    String issuer = "TestIssuer";
    Map<String, Object> claims = new HashMap<>();

    Token token = jwtUtil.generateToken(subject, issuer, claims);

    assertThat(token).isNotNull();
    assertThat(token.token()).isNotBlank();
  }

  @Test
  void getClaims_extractsClaimsFromValidToken() {
    String subject = "test@example.com";
    String issuer = "TestIssuer";
    Map<String, Object> claims = new HashMap<>();
    claims.put("role", "ADMIN");
    claims.put("userId", 123);

    Token token = jwtUtil.generateToken(subject, issuer, claims);
    Claims extractedClaims = jwtUtil.getClaims(token.token());

    assertThat(extractedClaims).isNotNull();
    assertThat(extractedClaims.getSubject()).isEqualTo(subject);
    assertThat(extractedClaims.getIssuer()).isEqualTo(issuer);
    assertThat(extractedClaims.get("role")).isEqualTo("ADMIN");
    assertThat(extractedClaims.get("userId")).isEqualTo(123);
    assertThat(extractedClaims.getIssuedAt()).isNotNull();
    assertThat(extractedClaims.getExpiration()).isNotNull();
  }

  @Test
  void getClaims_withNullIssuer_extractsClaimsCorrectly() {
    String subject = "test@example.com";
    Map<String, Object> claims = new HashMap<>();
    claims.put("role", "USER");

    Token token = jwtUtil.generateToken(subject, null, claims);
    Claims extractedClaims = jwtUtil.getClaims(token.token());

    assertThat(extractedClaims).isNotNull();
    assertThat(extractedClaims.getSubject()).isEqualTo(subject);
    assertThat(extractedClaims.getIssuer()).isNull();
    assertThat(extractedClaims.get("role")).isEqualTo("USER");
  }

  @Test
  void tokenExpiration_isSetCorrectly() {
    String subject = "test@example.com";
    String issuer = "TestIssuer";
    Map<String, Object> claims = new HashMap<>();

    long beforeGeneration = System.currentTimeMillis();
    Token token = jwtUtil.generateToken(subject, issuer, claims);
    long afterGeneration = System.currentTimeMillis();

    long expectedMinExpiration = beforeGeneration + TEST_EXPIRATION;
    long expectedMaxExpiration = afterGeneration + TEST_EXPIRATION;

    assertThat(token.expiresAt())
        .isGreaterThanOrEqualTo(expectedMinExpiration)
        .isLessThanOrEqualTo(expectedMaxExpiration);
  }

  @Test
  void roundTrip_generateAndExtractToken_preservesAllData() {
    String subject = "user@example.com";
    String issuer = "MyApp";
    Map<String, Object> claims = new HashMap<>();
    claims.put("tenantId", "tenant-123");
    claims.put("permissions", "read,write");
    claims.put("customField", true);

    Token token = jwtUtil.generateToken(subject, issuer, claims);
    Claims extractedClaims = jwtUtil.getClaims(token.token());

    assertThat(extractedClaims.getSubject()).isEqualTo(subject);
    assertThat(extractedClaims.getIssuer()).isEqualTo(issuer);
    assertThat(extractedClaims.get("tenantId")).isEqualTo("tenant-123");
    assertThat(extractedClaims.get("permissions")).isEqualTo("read,write");
    assertThat(extractedClaims.get("customField")).isEqualTo(true);
  }
}
