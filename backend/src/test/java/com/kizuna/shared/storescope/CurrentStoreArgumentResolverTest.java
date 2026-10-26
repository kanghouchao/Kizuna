package com.kizuna.shared.storescope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.shared.exception.ServiceException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.ServletWebRequest;

class CurrentStoreArgumentResolverTest {

  private final StoreScopeHolder storeScopeHolder = new StoreScopeHolder();
  private final StoreExistenceCheck existence = mock(StoreExistenceCheck.class);
  private final CurrentStoreArgumentResolver resolver =
      new CurrentStoreArgumentResolver(storeScopeHolder, existence);

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    storeScopeHolder.clear();
    ThreadContext.clearAll();
  }

  /** 引数の形を切り替えて MethodParameter を組み立てるための土台。 */
  static class Handlers {
    void handle(
        @CurrentStore Long required,
        @CurrentStore(required = false) Long optional,
        Long unannotated,
        @CurrentStore String wrongType) {}
  }

  private static MethodParameter parameter(int index) throws NoSuchMethodException {
    Method method =
        Handlers.class.getDeclaredMethod(
            "handle", Long.class, Long.class, Long.class, String.class);
    return new MethodParameter(method, index);
  }

  private static final int REQUIRED = 0;
  private static final int OPTIONAL = 1;

  private Long resolve(int parameterIndex, String header) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    if (header != null) request.addHeader("X-Store-ID", header);
    return resolver.resolveArgument(
        parameter(parameterIndex), null, new ServletWebRequest(request), null);
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

  private void assertBadRequest(int parameterIndex, String header, String message) {
    assertThatThrownBy(() -> resolve(parameterIndex, header))
        .isInstanceOf(ServiceException.class)
        .hasMessage(message);
    assertThat(ThreadContext.get(CurrentStoreArgumentResolver.MDC_STORE_ID)).isNull();
  }

  private void assertForbidden(String header) {
    assertThatThrownBy(() -> resolve(REQUIRED, header)).isInstanceOf(AccessDeniedException.class);
    assertThat(ThreadContext.get(CurrentStoreArgumentResolver.MDC_STORE_ID)).isNull();
  }

  @Test
  @DisplayName("ヘッダが無ければ必須の引数は 400、任意の引数は null（実在確認もしない）")
  void absentHeader() throws Exception {
    assertBadRequest(REQUIRED, null, "店舗を指定してください");
    assertThat(resolve(OPTIONAL, null)).isNull();
    assertThat(ThreadContext.get(CurrentStoreArgumentResolver.MDC_STORE_ID)).isNull();
    verifyNoInteractions(existence);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  @DisplayName("空白だけのヘッダは無いのと同じ扱い")
  void blankHeaderIsAbsent(String header) throws Exception {
    assertBadRequest(REQUIRED, header, "店舗を指定してください");
    assertThat(resolve(OPTIONAL, header)).isNull();
    verifyNoInteractions(existence);
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "-1", "1.5", "99999999999999999999"})
  @DisplayName("数値でない・long 範囲を超えるヘッダは必須でも任意でも 400")
  void malformedHeaderIsBadRequest(String header) {
    authenticate(new StoreScope(true, Set.of()), "PERM_ORDER_MANAGE");
    assertBadRequest(REQUIRED, header, "店舗 ID の形式が正しくありません");
    assertBadRequest(OPTIONAL, header, "店舗 ID の形式が正しくありません");
    verifyNoInteractions(existence);
  }

  @Test
  @DisplayName("未認証（公開サイト）は実在する任意の店舗を名乗れ、任意の引数でも同じ検証を通ること")
  void anonymousNamesAnyExistingStore() throws Exception {
    when(existence.exists(42L)).thenReturn(true);
    assertThat(resolve(REQUIRED, "42")).isEqualTo(42L);
    assertThat(resolve(OPTIONAL, "42")).isEqualTo(42L);
  }

  @Test
  @DisplayName("未認証が実在しない店舗を名乗ると 400")
  void anonymousNonexistentStoreIsBadRequest() {
    assertBadRequest(REQUIRED, "42", "店舗が見つかりません");
    assertBadRequest(OPTIONAL, "42", "店舗が見つかりません");
  }

  @Test
  @DisplayName("JwtAuthenticationToken 以外の認証（匿名認証・利用者名パスワード）は未認証と同じ扱い")
  void nonJwtAuthenticationIsTreatedAsAnonymous() throws Exception {
    when(existence.exists(7L)).thenReturn(true);
    for (Authentication authentication :
        List.<Authentication>of(
            new AnonymousAuthenticationToken(
                "key", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))),
            UsernamePasswordAuthenticationToken.authenticated(
                "user", null, List.of(new SimpleGrantedAuthority("ROLE_CAST"))))) {
      SecurityContextHolder.getContext().setAuthentication(authentication);
      assertThat(resolve(REQUIRED, "7")).isEqualTo(7L);
    }
  }

  @Test
  @DisplayName("権限（PERM_）を持たない CAST / MEMBER は店舗集合に店舗があっても 403（招待で店舗を持つ CAST を塞ぐ）")
  void subjectWithoutPermissionIsDeniedEvenWithinScope() {
    when(existence.exists(1L)).thenReturn(true);
    authenticate(new StoreScope(false, Set.of(1L)), "ROLE_CAST");
    assertForbidden("1");
  }

  @Test
  @DisplayName("権限保持者でも作用域を解決できなければ 403（fail-closed）")
  void unresolvedScopeIsDenied() {
    when(existence.exists(1L)).thenReturn(true);
    authenticate(null, "PERM_ORDER_MANAGE");
    assertForbidden("1");
  }

  @Test
  @DisplayName("本人の店舗集合に含まれない店舗を名乗ると 403")
  void storeOutsideScopeIsDenied() {
    when(existence.exists(2L)).thenReturn(true);
    authenticate(new StoreScope(false, Set.of(1L)), "PERM_ORDER_MANAGE");
    assertForbidden("2");
  }

  @Test
  @DisplayName("集合の外の実在しない店舗も 403 で、実在確認まで進まない（不在と授権外を区別させない）")
  void nonexistentStoreOutsideScopeIsDeniedBeforeExistenceCheck() {
    when(existence.exists(anyLong())).thenReturn(false);
    authenticate(new StoreScope(false, Set.of(1L)), "PERM_ORDER_MANAGE");
    assertForbidden("999");
    verify(existence, never()).exists(anyLong());
  }

  @Test
  @DisplayName("集合の内の店舗でも実在しなければ 400")
  void nonexistentStoreInsideScopeIsBadRequest() {
    authenticate(new StoreScope(false, Set.of(1L)), "PERM_ORDER_MANAGE");
    assertBadRequest(REQUIRED, "1", "店舗が見つかりません");
  }

  @Test
  @DisplayName("権限保持者は集合の内の店舗を名乗れ、ALL_STORES は実在する任意の店舗を名乗れること")
  void permissionHolderWithinScopeResolvesStore() throws Exception {
    when(existence.exists(anyLong())).thenReturn(true);
    authenticate(new StoreScope(false, Set.of(1L)), "PERM_ORDER_MANAGE");
    assertThat(resolve(REQUIRED, "1")).isEqualTo(1L);

    authenticate(new StoreScope(true, Set.of()), "PERM_STORE_VIEW");
    assertThat(resolve(REQUIRED, "999")).isEqualTo(999L);
  }

  @Test
  @DisplayName("X-Role は読まない — 送られても判定に影響しないこと")
  void roleHeaderIsIgnored() throws Exception {
    when(existence.exists(42L)).thenReturn(true);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Store-ID", "42");
    request.addHeader("X-Role", "platform");

    assertThat(
            resolver.resolveArgument(
                parameter(REQUIRED), null, new ServletWebRequest(request), null))
        .isEqualTo(42L);
  }

  @Test
  @DisplayName("全検証を通ったときだけ店舗 id をログの MDC に載せること")
  void mdcCarriesTheStoreOnlyAfterSuccess() throws Exception {
    authenticate(new StoreScope(false, Set.of(1L)), "PERM_ORDER_MANAGE");
    assertBadRequest(REQUIRED, "1", "店舗が見つかりません");

    when(existence.exists(1L)).thenReturn(true);
    assertThat(resolve(REQUIRED, "1")).isEqualTo(1L);
    assertThat(ThreadContext.get(CurrentStoreArgumentResolver.MDC_STORE_ID)).isEqualTo("1");
  }

  @Test
  @DisplayName("@CurrentStore の付いた Long 引数だけを扱うこと")
  void supportsOnlyAnnotatedLongParameters() throws Exception {
    assertThat(resolver.supportsParameter(parameter(0))).isTrue();
    assertThat(resolver.supportsParameter(parameter(1))).isTrue();
    assertThat(resolver.supportsParameter(parameter(2))).isFalse();
    assertThat(resolver.supportsParameter(parameter(3))).isFalse();
  }
}
