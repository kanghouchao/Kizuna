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
import org.springframework.web.method.HandlerMethod;

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

  /** 認証済みだが tenantId claim を持たない Claims（央端 / legacy）を模擬する。 */
  private void authenticateWithoutTenantId() {
    Claims claims = Jwts.claims().issuer("CentralAuth").build();
    PreAuthenticatedAuthenticationToken authentication =
        new PreAuthenticatedAuthenticationToken(
            "user", "token", List.of(new SimpleGrantedAuthority("CENTRAL_USER")));
    authentication.setDetails(claims);
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  /** テスト用ハンドラ: {@link TenantOptional} の有無を切り替えて HandlerMethod を組み立てるための土台。 */
  static class Handlers {
    @TenantOptional
    public void optional() {}

    public void required() {}
  }

  private HandlerMethod handlerMethod(String methodName) throws NoSuchMethodException {
    return new HandlerMethod(new Handlers(), Handlers.class.getMethod(methodName));
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
  @DisplayName("X-Role が tenant でなければテナント文脈を設定せず、@TenantOptional の無いハンドラは 403 で拒否すること")
  void preHandle_ignoresNonTenantRole() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Role", "central");
    request.addHeader("X-Tenant-ID", "42");
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean result = interceptor.preHandle(request, response, new Object());

    assertThat(result).isFalse();
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(tenantContext.isTenant()).isFalse();
  }

  @Test
  @DisplayName("X-Tenant-ID が数値でなければテナント文脈を設定せず、@TenantOptional の無いハンドラは 403 で拒否すること")
  void preHandle_ignoresNonNumericTenantId() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Role", "tenant");
    request.addHeader("X-Tenant-ID", "abc");
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean result = interceptor.preHandle(request, response, new Object());

    assertThat(result).isFalse();
    assertThat(response.getStatus()).isEqualTo(403);
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
  @DisplayName("テナント文脈を解決できなくても @TenantOptional 付きハンドラは素通りし、文脈を設定しないこと")
  void preHandle_allowsTenantOptionalHandlerWhenContextUnresolved() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean result = interceptor.preHandle(request, response, handlerMethod("optional"));

    assertThat(result).isTrue();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(tenantContext.isTenant()).isFalse();
  }

  @Test
  @DisplayName("テナント文脈を解決できず @TenantOptional も無いハンドラは 403 で拒否すること")
  void preHandle_rejectsNonOptionalHandlerWhenContextUnresolved() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean result = interceptor.preHandle(request, response, handlerMethod("required"));

    assertThat(result).isFalse();
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(tenantContext.isTenant()).isFalse();
  }

  @Test
  @DisplayName("ハンドラが HandlerMethod でなく文脈も解決できない場合は 403 で拒否すること")
  void preHandle_rejectsNonHandlerMethodWhenContextUnresolved() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean result = interceptor.preHandle(request, response, new Object());

    assertThat(result).isFalse();
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(tenantContext.isTenant()).isFalse();
  }

  @Test
  @DisplayName("認証済みだが tenantId claim が無いトークンが X-Role:tenant で別テナントを名乗ると 403 で拒否すること（#294）")
  void preHandle_rejectsAuthenticatedTokenWithoutTenantIdClaimSpoofingHeader() {
    authenticateWithoutTenantId();
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
  @DisplayName("未認証で X-Tenant-ID が long 範囲を超える桁数なら 400 で拒否すること（#288）")
  void preHandle_rejectsOverflowingTenantIdHeaderWith400() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Role", "tenant");
    request.addHeader("X-Tenant-ID", "99999999999999999999");
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean result = interceptor.preHandle(request, response, new Object());

    assertThat(result).isFalse();
    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(tenantContext.isTenant()).isFalse();
  }

  @Test
  @DisplayName("認証済みテナント JWT でも X-Tenant-ID が long 範囲を超えるなら 400 で拒否すること（#288）")
  void preHandle_rejectsOverflowingHeaderForAuthenticatedTenant() {
    authenticateWithTenantId(1L);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Role", "tenant");
    request.addHeader("X-Tenant-ID", "99999999999999999999");
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean result = interceptor.preHandle(request, response, new Object());

    assertThat(result).isFalse();
    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(tenantContext.isTenant()).isFalse();
  }

  /**
   * 平台トークン（storeScopeType/storeIds claim を持ち tenantId claim は持たない）を模擬する。 storeIds には List
   * を渡す（ALL_STORES では無視される）。
   */
  private void authenticateWithPlatformScope(String scopeType, Object storeIds) {
    Claims claims =
        Jwts.claims().add("storeScopeType", scopeType).add("storeIds", storeIds).build();
    PreAuthenticatedAuthenticationToken authentication =
        new PreAuthenticatedAuthenticationToken(
            "platform-user", "token", List.of(new SimpleGrantedAuthority("ROLE_STORE_MANAGER")));
    authentication.setDetails(claims);
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  @Test
  @DisplayName("平台 SPECIFIC{1} が X-Tenant-ID:1 を名乗れば授権内としてテナント文脈を設定すること")
  void preHandle_platformSpecific_allowsAuthorizedStore() {
    authenticateWithPlatformScope("SPECIFIC_STORES", List.of(1));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Role", "tenant");
    request.addHeader("X-Tenant-ID", "1");

    boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

    assertThat(result).isTrue();
    assertThat(tenantContext.getTenantId()).isEqualTo(1L);
  }

  @Test
  @DisplayName("平台 SPECIFIC{1} が非授権店舗 X-Tenant-ID:2 を名乗ると 403 で拒否し文脈を設定しないこと")
  void preHandle_platformSpecific_rejectsUnauthorizedStore() {
    authenticateWithPlatformScope("SPECIFIC_STORES", List.of(1));
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
  @DisplayName("平台 ALL_STORES は任意の X-Tenant-ID をテナント文脈に設定すること")
  void preHandle_platformAllStores_allowsAnyStore() {
    authenticateWithPlatformScope("ALL_STORES", List.of());
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Role", "tenant");
    request.addHeader("X-Tenant-ID", "999");

    boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

    assertThat(result).isTrue();
    assertThat(tenantContext.getTenantId()).isEqualTo(999L);
  }

  @Test
  @DisplayName("平台トークンでもテナントヘッダが無ければ @TenantOptional 無しハンドラは 403 で拒否すること")
  void preHandle_platformScope_noHeader_rejectsRequiredHandler() throws Exception {
    authenticateWithPlatformScope("SPECIFIC_STORES", List.of(1));
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean result = interceptor.preHandle(request, response, handlerMethod("required"));

    assertThat(result).isFalse();
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(tenantContext.isTenant()).isFalse();
  }

  @Test
  @DisplayName("平台トークンでもテナントヘッダが無ければ @TenantOptional 付きハンドラは素通りし文脈を設定しないこと")
  void preHandle_platformScope_noHeader_allowsTenantOptionalHandler() throws Exception {
    authenticateWithPlatformScope("SPECIFIC_STORES", List.of(1));
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean result = interceptor.preHandle(request, response, handlerMethod("optional"));

    assertThat(result).isTrue();
    assertThat(tenantContext.isTenant()).isFalse();
  }

  @Test
  @DisplayName("平台トークンで X-Role が欠落し X-Tenant-ID のみなら店舗文脈は成立せず 403 で拒否すること")
  void preHandle_platformScope_missingRoleHeader_rejects() {
    authenticateWithPlatformScope("SPECIFIC_STORES", List.of(1));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Tenant-ID", "1");
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean result = interceptor.preHandle(request, response, new Object());

    assertThat(result).isFalse();
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(tenantContext.isTenant()).isFalse();
  }

  @Test
  @DisplayName("storeIds claim が不正な平台トークンは StoreScope が解決できず 403 で拒否すること（fail-closed）")
  void preHandle_platformScope_invalidStoreIds_rejects() {
    authenticateWithPlatformScope("SPECIFIC_STORES", List.of("not-a-number"));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Role", "tenant");
    request.addHeader("X-Tenant-ID", "1");
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
