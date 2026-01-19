package com.kizuna.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.config.filter.JwtAuthenticationFilter;
import com.kizuna.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

  @Mock private JwtUtil jwtUtil;
  @Mock private RedisTemplate<String, Object> redisTemplate;
  @Mock private FilterChain filterChain;

  @InjectMocks private JwtAuthenticationFilter filter;

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void doFilter_skipsWhenAuthorizationHeaderMissing() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verify(jwtUtil, never()).getClaims(any());
  }

  @Test
  void doFilter_rejectsBlacklistedToken() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer token-123");
    request.setRequestURI("/central/me");

    MockHttpServletResponse response = new MockHttpServletResponse();
    Claims claims = org.mockito.Mockito.mock(Claims.class);

    when(jwtUtil.getClaims("token-123")).thenReturn(claims);
    when(redisTemplate.hasKey("blacklist:tokens:token-123")).thenReturn(true);

    filter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void doFilter_populatesSecurityContextForValidToken() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer tenant-token");
    request.setRequestURI("/tenant/pages");

    MockHttpServletResponse response = new MockHttpServletResponse();
    Claims claims = org.mockito.Mockito.mock(Claims.class);
    Date expiry = Date.from(Instant.now().plusSeconds(60));
    when(claims.getExpiration()).thenReturn(expiry);
    when(claims.getSubject()).thenReturn("alice@example.com");
    when(claims.get("authorities", List.class)).thenReturn(List.of("ROLE_TENANT_ADMIN"));

    when(jwtUtil.getClaims("tenant-token")).thenReturn(claims);
    when(redisTemplate.hasKey("blacklist:tokens:tenant-token")).thenReturn(false);

    filter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    assertThat(authentication).isNotNull();
    assertThat(authentication.getName()).isEqualTo("alice@example.com");
    assertThat(authentication.getAuthorities())
        .extracting(String::valueOf)
        .contains("ROLE_TENANT_ADMIN");
    assertThat(authentication.getDetails()).isEqualTo(claims);
  }
}
