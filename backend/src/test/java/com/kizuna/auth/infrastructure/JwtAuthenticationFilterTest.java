package com.kizuna.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

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
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

  @Mock private JwtUtil jwtUtil;

  @Mock private TokenBlacklistService tokenBlacklistService;

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
  @DisplayName("PlatformAuth 発行のトークンは /platform 配下で認証されること")
  void platformTokenOnPlatformPath() throws Exception {
    when(tokenBlacklistService.isBlacklisted("token")).thenReturn(false);
    assertThat(authenticated("/platform/me", "PlatformAuth")).isTrue();
  }

  @Test
  @DisplayName("PlatformAuth 発行のトークンは /platform/configs で認証されること（過橋 #324）")
  void platformTokenOnPlatformConfigsPath() throws Exception {
    when(tokenBlacklistService.isBlacklisted("token")).thenReturn(false);
    assertThat(authenticated("/platform/configs", "PlatformAuth")).isTrue();
  }

  @Test
  @DisplayName("PlatformAuth 発行のトークンは /store 配下で認証されること（過橋 #324）")
  void platformTokenOnStorePath() throws Exception {
    when(tokenBlacklistService.isBlacklisted("token")).thenReturn(false);
    assertThat(authenticated("/store/orders", "PlatformAuth")).isTrue();
  }

  @Test
  @DisplayName("非 PlatformAuth 発行のトークンは制限ドメインで認証されないこと")
  void nonPlatformTokenOnRestrictedPath() throws Exception {
    assertThat(authenticated("/store/orders", "OtherAuth")).isFalse();
  }

  @Test
  @DisplayName("ドメイン外のパスでは issuer を制限しないこと")
  void anyIssuerOnDomainFreePath() throws Exception {
    when(tokenBlacklistService.isBlacklisted("token")).thenReturn(false);
    assertThat(authenticated("/files/upload", "OtherAuth")).isTrue();
  }

  @Test
  @DisplayName("ブラックリスト登録済みのトークンは認証されないこと")
  void blacklistedToken() throws Exception {
    when(tokenBlacklistService.isBlacklisted("token")).thenReturn(true);
    assertThat(authenticated("/platform/configs", "PlatformAuth")).isFalse();
  }

  @Test
  @DisplayName("ユーザー単位ブラックリスト登録済み（停止済みユーザー）のトークンは認証されないこと（#403）")
  void userBlacklistedToken() throws Exception {
    when(tokenBlacklistService.isBlacklisted("token")).thenReturn(false);
    when(tokenBlacklistService.isUserBlacklisted("user")).thenReturn(true);
    assertThat(authenticated("/platform/configs", "PlatformAuth")).isFalse();
  }
}
