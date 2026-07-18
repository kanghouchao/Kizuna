package com.kizuna.shared.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import org.aspectj.lang.ProceedingJoinPoint;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantFilterEnableTest {

  @Mock private EntityManager entityManager;
  @Mock private Session session;
  @Mock private Filter filter;
  @Mock private ProceedingJoinPoint pjp;

  private TenantContext tenantContext;
  private TenantFilterEnable aspect;

  @BeforeEach
  void setUp() {
    tenantContext = new TenantContext();
    aspect = new TenantFilterEnable(entityManager, tenantContext);
  }

  @Test
  @DisplayName("テナント文脈があれば tenantFilter を現在のテナント ID で有効化して処理を続行すること")
  void enablesFilterWhenTenantContextPresent() throws Throwable {
    tenantContext.setTenantId(42L);
    when(entityManager.unwrap(Session.class)).thenReturn(session);
    when(session.enableFilter("tenantFilter")).thenReturn(filter);
    when(pjp.proceed()).thenReturn("result");

    Object result = aspect.enableTenantFilterForTenantServiceMethods(pjp);

    assertThat(result).isEqualTo("result");
    verify(filter).setParameter("storeId", 42L);
    verify(pjp).proceed();
  }

  @Test
  @DisplayName("テナント文脈が無ければフィルタを有効化せず処理だけ続行すること")
  void skipsFilterWithoutTenantContext() throws Throwable {
    when(pjp.proceed()).thenReturn("result");

    Object result = aspect.enableTenantFilterForTenantServiceMethods(pjp);

    assertThat(result).isEqualTo("result");
    verify(entityManager, never()).unwrap(Session.class);
    verify(pjp).proceed();
  }
}
