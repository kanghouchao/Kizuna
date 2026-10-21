package com.kizuna.user.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.user.application.StoreScopeResolver;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class StoreScopeInstallingInterceptorTest {

  private final StoreScopeHolder holder = new StoreScopeHolder();
  private final StoreScopeResolver resolver = mock(StoreScopeResolver.class);
  private final StoreScopeInstallingInterceptor interceptor =
      new StoreScopeInstallingInterceptor(holder, resolver);

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
    holder.clear();
  }

  private Jwt authenticate() {
    Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("actor").build();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("PERM_X"))));
    return jwt;
  }

  @Test
  @DisplayName("認証済み要求では解決器を差し込み、解決は保持器が最初に読まれるまで遅延する")
  void installsLazyResolverForAuthenticatedRequest() {
    Jwt jwt = authenticate();
    StoreScope scope = new StoreScope(false, Set.of(1L));
    when(resolver.resolve(jwt)).thenReturn(scope);

    boolean proceed =
        interceptor.preHandle(
            new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());

    assertThat(proceed).isTrue();
    verify(resolver, never()).resolve(any());
    assertThat(holder.current()).isSameAs(scope);
    assertThat(holder.current()).isSameAs(scope);
    verify(resolver).resolve(jwt);
  }

  @Test
  @DisplayName("未認証・匿名の要求では何も差し込まず、保持器は null のまま")
  void leavesHolderEmptyWithoutJwt() {
    assertThat(
            interceptor.preHandle(
                new MockHttpServletRequest(), new MockHttpServletResponse(), new Object()))
        .isTrue();
    assertThat(holder.current()).isNull();

    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "key", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
    assertThat(
            interceptor.preHandle(
                new MockHttpServletRequest(), new MockHttpServletResponse(), new Object()))
        .isTrue();
    assertThat(holder.current()).isNull();
    verify(resolver, never()).resolve(any());
  }

  @Test
  @DisplayName("afterCompletion は例外の有無によらず保持器を消す（スレッドは再利用される）")
  void afterCompletionClearsHolder() {
    authenticate();
    when(resolver.resolve(any())).thenReturn(new StoreScope(true, Set.of()));
    interceptor.preHandle(
        new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());
    assertThat(holder.current()).isNotNull();

    interceptor.afterCompletion(
        new MockHttpServletRequest(),
        new MockHttpServletResponse(),
        new Object(),
        new IllegalStateException("boom"));

    assertThat(holder.current()).isNull();
  }
}
