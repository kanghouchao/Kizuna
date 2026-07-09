package com.kizuna.shared.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

class TenantIdInterceptorTest {

  private TenantContext tenantContext;
  private TenantIdInterceptor interceptor;

  @BeforeEach
  void setUp() {
    tenantContext = new TenantContext();
    interceptor = new TenantIdInterceptor(tenantContext);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  /** 認証済みリクエストを模擬する（details に tenantId claim を持つ実 Claims をセット）。 */
  private void authenticateWithTenantId(long tenantId) {
    Claims claims = Jwts.claims().add("tenantId", tenantId).build();
    PreAuthenticatedAuthenticationToken authentication =
        new PreAuthenticatedAuthenticationToken(
            "user", "token", List.of(new SimpleGrantedAuthority("STORE_USER")));
    authentication.setDetails(claims);
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  @Test
  @DisplayName("X-Role が tenant かつ X-Tenant-ID が数値ならテナント文脈を設定すること")
  void preHandle_setsTenantIdForTenantRole() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Role", "tenant");
    request.addHeader("X-Tenant-ID", "42");

    boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

    assertThat(result).isTrue();
    assertThat(tenantContext.getTenantId()).isEqualTo(42L);
  }

  @Test
  @DisplayName("X-Role が tenant でなければテナント文脈を設定しないこと")
  void preHandle_ignoresNonTenantRole() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Role", "central");
    request.addHeader("X-Tenant-ID", "42");

    boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

    assertThat(result).isTrue();
    assertThat(tenantContext.isTenant()).isFalse();
  }

  @Test
  @DisplayName("X-Tenant-ID が数値でなければテナント文脈を設定しないこと")
  void preHandle_ignoresNonNumericTenantId() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Role", "tenant");
    request.addHeader("X-Tenant-ID", "abc");

    boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

    assertThat(result).isTrue();
    assertThat(tenantContext.isTenant()).isFalse();
  }

  @Test
  @DisplayName("認証済みで JWT の tenantId claim と X-Tenant-ID ヘッダが不一致なら 403 で拒否し文脈を設定しないこと")
  void preHandle_rejectsWhenJwtTenantIdMismatchesHeader() {
    authenticateWithTenantId(1L);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Role", "tenant");
    request.addHeader("X-Tenant-ID", "2");
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean result = interceptor.preHandle(request, response, new Object());

    assertThat(result).isFalse();
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(tenantContext.isTenant()).isFalse();
  }

  @Test
  @DisplayName("認証済みで JWT の tenantId claim と X-Tenant-ID ヘッダが一致すればテナント文脈を設定すること")
  void preHandle_allowsWhenJwtTenantIdMatchesHeader() {
    authenticateWithTenantId(42L);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Role", "tenant");
    request.addHeader("X-Tenant-ID", "42");

    boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

    assertThat(result).isTrue();
    assertThat(tenantContext.getTenantId()).isEqualTo(42L);
  }

  @Test
  @DisplayName("認証はあるが details に Claims を持たない場合は従来通りヘッダのみでテナント文脈を設定すること")
  void preHandle_fallsBackToHeaderWhenAuthenticationHasNoClaims() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "key", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Role", "tenant");
    request.addHeader("X-Tenant-ID", "7");

    boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

    assertThat(result).isTrue();
    assertThat(tenantContext.getTenantId()).isEqualTo(7L);
  }

  @Test
  @DisplayName("認証済みで tenantId claim があれば X-Tenant-ID ヘッダが無くても claim からテナント文脈を設定すること")
  void preHandle_setsTenantIdFromClaimWhenHeaderMissing() {
    authenticateWithTenantId(5L);
    MockHttpServletRequest request = new MockHttpServletRequest();

    boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

    assertThat(result).isTrue();
    assertThat(tenantContext.getTenantId()).isEqualTo(5L);
  }

  @Test
  @DisplayName("認証済みで tenantId claim があれば X-Tenant-ID が不正形式でも claim からテナント文脈を設定すること")
  void preHandle_setsTenantIdFromClaimWhenHeaderMalformed() {
    authenticateWithTenantId(5L);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Tenant-ID", "not-a-number");

    boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

    assertThat(result).isTrue();
    assertThat(tenantContext.getTenantId()).isEqualTo(5L);
  }

  @Test
  @DisplayName("認証済みで X-Tenant-ID が別テナントを指すなら X-Role の有無に関係なく 403 で拒否すること")
  void preHandle_rejectsMismatchEvenWithoutRoleHeader() {
    authenticateWithTenantId(1L);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Tenant-ID", "2");
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean result = interceptor.preHandle(request, response, new Object());

    assertThat(result).isFalse();
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(tenantContext.isTenant()).isFalse();
  }

  @Test
  @DisplayName("afterCompletion でテナント文脈がクリアされること")
  void afterCompletion_clearsTenantContext() {
    tenantContext.setTenantId(42L);

    interceptor.afterCompletion(
        new MockHttpServletRequest(), new MockHttpServletResponse(), new Object(), null);

    assertThat(tenantContext.isTenant()).isFalse();
  }
}
