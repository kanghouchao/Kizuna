package com.kizuna.shared.storescope;

import jakarta.persistence.EntityManager;
import lombok.AllArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * {@code @StoreScoped} メソッドの呼び出しを囲み、現在の Session に Hibernate の {@code storeFilter} を有効化する。
 * 店舗文脈が無い場合は何もしない — 絞り込み無しのまま実行される。
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
