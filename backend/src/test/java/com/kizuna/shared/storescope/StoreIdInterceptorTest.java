package com.kizuna.shared.storescope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.shared.exception.ServiceException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.method.HandlerMethod;

class StoreIdInterceptorTest {

  private StoreContext storeContext;
  private StoreScopeHolder storeScopeHolder;
  private StoreIdInterceptor interceptor;

  @BeforeEach
  void setUp() {
    storeContext = new StoreContext();
    storeScopeHolder = new StoreScopeHolder();
    interceptor = new StoreIdInterceptor(storeContext, storeScopeHolder);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    storeScopeHolder.clear();
  }

  /** テスト用ハンドラ: {@link StoreOptional} の有無を切り替えて HandlerMethod を組み立てるための土台。 */
  static class Handlers {
    @StoreOptional
    public void optional() {}

    public void required() {}
  }

  private HandlerMethod handlerMethod(String methodName) throws NoSuchMethodException {
    return new HandlerMethod(new Handlers(), Handlers.class.getMethod(methodName));
  }

  private static MockHttpServletRequest withStoreHeader(String value) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    if (value != null) request.addHeader("X-Store-ID", value);
    return request;
  }

  /** 認証済み主体を模擬する。authorities と保持器の作用域を別々に与え、権限の有無と集合の有無を独立に動かす。 */
  private void authenticate(StoreScope scope, String... authorities) {
    Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS256").subject("user").build();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new JwtAuthenticationToken(
                jwt, List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList()));
    storeScopeHolder.install(() -> scope);
  }

  private void assertDenied(MockHttpServletRequest request, Object handler) {
    assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), handler))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(storeContext.hasStoreId()).isFalse();
  }

  @Test
  @DisplayName("未認証（公開サイト）は X-Store-ID だけで店舗文脈を名乗れること")
  void anonymousHeaderEstablishesContext() {
    assertThat(
            interceptor.preHandle(
                withStoreHeader("42"), new MockHttpServletResponse(), new Object()))
        .isTrue();
    assertThat(storeContext.getStoreId()).isEqualTo(42L);
  }

  @Test
  @DisplayName("非 JwtAuthenticationToken（匿名認証）も未認証と同じ扱い")
  void anonymousAuthenticationEstablishesContextFromHeader() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "key", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

    assertThat(
            interceptor.preHandle(
                withStoreHeader("7"), new MockHttpServletResponse(), new Object()))
        .isTrue();
    assertThat(storeContext.getStoreId()).isEqualTo(7L);
  }

  @Test
  @DisplayName("X-Role は読まない — 送られても店舗文脈の判定に影響しないこと")
  void roleHeaderIsIgnored() {
    MockHttpServletRequest request = withStoreHeader("42");
    request.addHeader("X-Role", "platform");

    assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
        .isTrue();
    assertThat(storeContext.getStoreId()).isEqualTo(42L);
  }

  @Test
  @DisplayName("X-Store-ID が数値でなければ店舗文脈は成立せず、@StoreOptional の無いハンドラは 403")
  void nonNumericHeaderIsNoContext() throws Exception {
    assertDenied(withStoreHeader("abc"), handlerMethod("required"));
    assertThat(
            interceptor.preHandle(
                withStoreHeader("abc"), new MockHttpServletResponse(), handlerMethod("optional")))
        .isTrue();
  }

  @Test
  @DisplayName("long 範囲を超える桁数は 400")
  void overflowingHeaderIs400() {
    assertThatThrownBy(
            () ->
                interceptor.preHandle(
                    withStoreHeader("99999999999999999999"),
                    new MockHttpServletResponse(),
                    new Object()))
        .isInstanceOf(ServiceException.class);
    assertThat(storeContext.hasStoreId()).isFalse();
  }

  @Test
  @DisplayName("店舗文脈を解決できず @StoreOptional も無いハンドラは 403、@StoreOptional 付きは素通り")
  void unresolvedContextFallsToStoreOptional() throws Exception {
    assertDenied(withStoreHeader(null), handlerMethod("required"));
    assertDenied(withStoreHeader(null), new Object());
    assertThat(
            interceptor.preHandle(
                withStoreHeader(null), new MockHttpServletResponse(), handlerMethod("optional")))
        .isTrue();
    assertThat(storeContext.hasStoreId()).isFalse();
  }

  @Test
  @DisplayName("権限保持者は本人の店舗集合に含まれる店舗を名乗れること（ALL_STORES は任意の店舗）")
  void permissionHolderWithinScopeEstablishesContext() {
    authenticate(new StoreScope(false, Set.of(1L)), "PERM_ORDER_MANAGE");
    assertThat(
            interceptor.preHandle(
                withStoreHeader("1"), new MockHttpServletResponse(), new Object()))
        .isTrue();
    assertThat(storeContext.getStoreId()).isEqualTo(1L);

    storeContext.clear();
    authenticate(new StoreScope(true, Set.of()), "PERM_STORE_VIEW");
    assertThat(
            interceptor.preHandle(
                withStoreHeader("999"), new MockHttpServletResponse(), new Object()))
        .isTrue();
    assertThat(storeContext.getStoreId()).isEqualTo(999L);
  }

  @Test
  @DisplayName("本人の店舗集合に含まれない店舗を名乗ると 403")
  void storeOutsideScopeIsDenied() {
    authenticate(new StoreScope(false, Set.of(1L)), "PERM_ORDER_MANAGE");
    assertDenied(withStoreHeader("2"), new Object());
  }

  @Test
  @DisplayName("権限（PERM_）を持たない CAST / MEMBER は店舗集合に店舗があっても 403（招待で店舗を持つ CAST を塞ぐ）")
  void subjectWithoutPermissionIsDeniedEvenWithinScope() {
    authenticate(new StoreScope(false, Set.of(1L)), "ROLE_CAST");
    assertDenied(withStoreHeader("1"), new Object());
  }

  @Test
  @DisplayName("権限保持者でも作用域を解決できなければ 403（fail-closed）")
  void unresolvedScopeIsDenied() {
    authenticate(null, "PERM_ORDER_MANAGE");
    assertDenied(withStoreHeader("1"), new Object());
  }

  @Test
  @DisplayName("認証済みが非数値の X-Store-ID で店舗を名乗ると 403")
  void authenticatedNonNumericHeaderIsDenied() throws Exception {
    authenticate(new StoreScope(true, Set.of()), "PERM_ORDER_MANAGE");
    assertDenied(withStoreHeader("abc"), handlerMethod("optional"));
  }

  @Test
  @DisplayName("認証済みでも X-Store-ID が無ければ @StoreOptional 判定へ落ちること")
  void authenticatedWithoutHeaderFallsToStoreOptional() throws Exception {
    authenticate(new StoreScope(true, Set.of()), "PERM_ORDER_MANAGE");
    assertDenied(withStoreHeader(null), handlerMethod("required"));
    assertThat(
            interceptor.preHandle(
                withStoreHeader(null), new MockHttpServletResponse(), handlerMethod("optional")))
        .isTrue();
  }

  @Test
  @DisplayName("afterCompletion で店舗文脈がクリアされること")
  void afterCompletion_clearsStoreContext() {
    storeContext.setStoreId(42L);

    interceptor.afterCompletion(
        new MockHttpServletRequest(), new MockHttpServletResponse(), new Object(), null);

    assertThat(storeContext.hasStoreId()).isFalse();
  }
}
