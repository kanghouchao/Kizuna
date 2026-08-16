package com.kizuna.shared.storescope;

import jakarta.persistence.EntityManager;
import lombok.AllArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * {@code @StoreScoped} メソッドの呼び出しを囲み、現在の Session に Hibernate の {@code storeFilter} を有効化する。
 * 店舗文脈が無い場合は何もしない — 絞り込み無しのまま実行される。
 *
 * <p>{@code @Order} は最低優先度で固定する。値を下げるとトランザクション advisor の外側へ出て、OSIV=off の共有 EntityManager が使い捨ての
 * Session を作って即閉じるため、フィルタは死んだ Session に掛かり本体は別 Session で走る（＝隔離が静默に外れる）。
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
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
