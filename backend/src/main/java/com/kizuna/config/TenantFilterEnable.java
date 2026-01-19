package com.kizuna.config;

import com.kizuna.config.interceptor.TenantContext;
import jakarta.persistence.EntityManager;
import lombok.AllArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Aspect that enables Hibernate tenant filtering for methods annotated with {@code @TenantScoped}.
 *
 * <p>When a method is annotated with {@code @TenantScoped}, this aspect intercepts the call and
 * enables the Hibernate {@code tenantFilter}, setting the tenant ID from the {@link TenantContext}.
 * This ensures that all database operations within the method are scoped to the current tenant.
 *
 * @author kanghouchao
 */
@Aspect
@Component
@Order
@AllArgsConstructor
public class TenantFilterEnable {

  private final EntityManager entityManager;
  private final TenantContext tenantContext;

  @Around(value = "@annotation(com.kizuna.config.TenantScoped)")
  public Object enableTenantFilterForTenantServiceMethods(ProceedingJoinPoint pjp)
      throws Throwable {
    if (tenantContext.isTenant()) {
      entityManager
          .unwrap(org.hibernate.Session.class)
          .enableFilter("tenantFilter")
          .setParameter("tenantId", tenantContext.getTenantId());
    }
    return pjp.proceed();
  }
}
