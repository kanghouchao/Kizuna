package com.kizuna.shared.storescope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.Claims;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Set;
import org.aspectj.lang.ProceedingJoinPoint;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class StoreSetFilterEnableTest {

  @Mock private EntityManager entityManager;
  @Mock private Session session;
  @Mock private Filter filter;
  @Mock private ProceedingJoinPoint pjp;

  private StoreSetFilterEnable aspect;

  @BeforeEach
  void setUp() {
    aspect = new StoreSetFilterEnable(entityManager);
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private void authenticateWith(Claims claims) {
    Authentication auth = mock(Authentication.class);
    lenient().when(auth.getDetails()).thenReturn(claims);
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @Test
  @DisplayName("授権集合を解決できない呼び出しは fail-closed で AccessDeniedException となり proceed されないこと")
  void failsClosedWhenScopeUnresolved() throws Throwable {
    // 認証コンテキスト未設定 → StoreScope 解決不能
    assertThatThrownBy(() -> aspect.enableStoreSetFilter(pjp))
        .isInstanceOf(AccessDeniedException.class);

    verify(pjp, never()).proceed();
  }

  @Test
  @DisplayName("ALL_STORES はフィルタを有効化せず処理だけ続行すること")
  void allStoresSkipsFilterAndProceeds() throws Throwable {
    Claims claims = mock(Claims.class);
    when(claims.get("storeScopeType", String.class)).thenReturn("ALL_STORES");
    authenticateWith(claims);
    when(pjp.proceed()).thenReturn("result");

    Object result = aspect.enableStoreSetFilter(pjp);

    assertThat(result).isEqualTo("result");
    verify(entityManager, never()).unwrap(Session.class);
    verify(pjp).proceed();
  }

  @Test
  @DisplayName("SPECIFIC_STORES は storeSetFilter を授権集合で有効化してから処理を続行すること")
  void specificStoresEnablesFilterWithStoreIds() throws Throwable {
    Claims claims = mock(Claims.class);
    when(claims.get("storeScopeType", String.class)).thenReturn("SPECIFIC_STORES");
    when(claims.get("storeIds")).thenReturn(List.of(1, 2));
    authenticateWith(claims);
    when(entityManager.unwrap(Session.class)).thenReturn(session);
    when(session.enableFilter("storeSetFilter")).thenReturn(filter);
    when(pjp.proceed()).thenReturn("result");

    Object result = aspect.enableStoreSetFilter(pjp);

    assertThat(result).isEqualTo("result");
    verify(filter).setParameterList("storeIds", Set.of(1L, 2L));
    verify(pjp).proceed();
  }
}
