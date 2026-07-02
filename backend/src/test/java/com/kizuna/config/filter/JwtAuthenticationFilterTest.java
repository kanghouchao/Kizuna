package com.kizuna.config.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.kizuna.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

  @Mock private JwtUtil jwtUtil;

  @Mock private RedisTemplate<String, Object> redisTemplate;

  @InjectMocks private JwtAuthenticationFilter filter;

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  /** 指定した issuer を持つ有効な Claims を生成する */
  private Claims buildClaims(String issuer) {
    return Jwts.claims()
        .issuer(issuer)
        .subject("user")
        .expiration(new Date(System.currentTimeMillis() + 60_000))
        .add("authorities", List.of("SYSTEM_CONFIG"))
        .build();
  }

  /** 指定パスに Bearer トークン付きリクエストを流し、認証結果を返す */
  private boolean authenticated(String path, String issuer) throws Exception {
    when(jwtUtil.getClaims("token")).thenReturn(buildClaims(issuer));
    MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
    request.addHeader("Authorization", "Bearer token");
    filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    return SecurityContextHolder.getContext().getAuthentication() != null;
  }

  @Test
  @DisplayName("CentralAuth 発行のトークンは /central 配下で認証されること")
  void centralTokenOnCentralPath() throws Exception {
    when(redisTemplate.hasKey("blacklist:tokens:token")).thenReturn(false);
    assertThat(authenticated("/central/configs", "CentralAuth")).isTrue();
  }

  @Test
  @DisplayName("TenantAuth 発行のトークンは /central 配下で認証されないこと")
  void tenantTokenOnCentralPath() throws Exception {
    assertThat(authenticated("/central/configs", "TenantAuth")).isFalse();
  }

  @Test
  @DisplayName("CentralAuth 発行のトークンは /tenant 配下で認証されないこと")
  void centralTokenOnTenantPath() throws Exception {
    assertThat(authenticated("/tenant/orders", "CentralAuth")).isFalse();
  }

  @Test
  @DisplayName("ドメイン外のパスでは issuer を制限しないこと")
  void anyIssuerOnDomainFreePath() throws Exception {
    when(redisTemplate.hasKey("blacklist:tokens:token")).thenReturn(false);
    assertThat(authenticated("/files/upload", "TenantAuth")).isTrue();
  }

  @Test
  @DisplayName("ブラックリスト登録済みのトークンは認証されないこと")
  void blacklistedToken() throws Exception {
    when(redisTemplate.hasKey("blacklist:tokens:token")).thenReturn(true);
    assertThat(authenticated("/central/configs", "CentralAuth")).isFalse();
  }
}
