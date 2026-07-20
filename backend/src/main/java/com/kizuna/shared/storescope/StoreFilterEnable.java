package com.kizuna.shared.storescope;

import jakarta.persistence.EntityManager;
import lombok.AllArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Aspect that enables Hibernate store filtering for methods annotated with {@code @StoreScoped}.
 *
 * <p>When a method is annotated with {@code @StoreScoped}, this aspect intercepts the call and
 * enables the Hibernate {@code storeFilter}, setting the store ID from the {@link StoreContext}.
 * This ensures that all database operations within the method are scoped to the current store.
 *
 * @author kanghouchao
 */
@Aspect
@Component
@Order
@AllArgsConstructor
public class StoreFilterEnable {

  private final EntityManager entityManager;
  private final StoreContext storeContext;

  @Around(value = "@annotation(com.kizuna.shared.storescope.StoreScoped)")
  public Object enableStoreFilterForStoreServiceMethods(ProceedingJoinPoint pjp) throws Throwable {
    if (storeContext.hasStoreId()) {
      entityManager
          .unwrap(org.hibernate.Session.class)
          .enableFilter("storeFilter")
          .setParameter("storeId", storeContext.getStoreId());
    }
    return pjp.proceed();
  }
}
