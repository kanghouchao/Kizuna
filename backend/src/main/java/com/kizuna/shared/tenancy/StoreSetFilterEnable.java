package com.kizuna.shared.tenancy;

import jakarta.persistence.EntityManager;
import lombok.AllArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * {@code @StoreSetScoped} が付いたメソッドで Hibernate の storeSetFilter を有効化する Aspect（#323 集合作用域）。
 *
 * <p>認証済み平台トークンの授権店舗集合を {@link StoreScope} で解決し、SPECIFIC_STORES の場合のみ storeSetFilter（{@code
 * tenant_id in (:storeIds)}）を有効化する。ALL_STORES はフィルタ無効＝全店可視。授権集合を解決できない呼び出しは fail-closed に {@link
 * AccessDeniedException} で拒否する。{@link TenantFilterEnable} と同型の {@code @Order} 指定（トランザクション advisor
 * との相対順序）を逐字踏襲する。
 */
@Aspect
@Component
@Order
@AllArgsConstructor
public class StoreSetFilterEnable {

  private final EntityManager entityManager;

  @Around(value = "@annotation(com.kizuna.shared.tenancy.StoreSetScoped)")
  public Object enableStoreSetFilter(ProceedingJoinPoint pjp) throws Throwable {
    StoreScope scope =
        StoreScope.fromAuthentication(SecurityContextHolder.getContext().getAuthentication());
    if (scope == null) {
      // fail-closed: 授権集合を解決できない呼び出しは進ませない
      throw new AccessDeniedException("店舗集合スコープを解決できません");
    }
    if (!scope.allStores()) {
      entityManager
          .unwrap(org.hibernate.Session.class)
          .enableFilter("storeSetFilter")
          .setParameterList("storeIds", scope.storeIds());
    }
    return pjp.proceed();
  }
}
