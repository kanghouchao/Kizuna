package com.kizuna.order.infrastructure;

import com.kizuna.order.api.dto.SelfMonthlyRemunerationStoreSummary;
import com.kizuna.shared.exception.NotFoundException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SelfMonthlyRemunerationQuery {
  private final EntityManager entityManager;

  private static final String STORES =
      """
      from com.kizuna.store.domain.Store s
      where exists (
        select e.id from com.kizuna.cast.domain.CastEnrollment e
        where e.storeId = s.id and e.castId = :personId)
      """;

  public Page<SelfMonthlyRemunerationStoreSummary> stores(Long personId, Pageable page) {
    var content =
        entityManager
            .createQuery(
                "select new com.kizuna.order.api.dto.SelfMonthlyRemunerationStoreSummary(s.id, s.name) "
                    + STORES
                    + " order by s.id asc",
                SelfMonthlyRemunerationStoreSummary.class)
            .setParameter("personId", personId)
            .setFirstResult((int) page.getOffset())
            .setMaxResults(page.getPageSize())
            .getResultList();
    long count =
        entityManager
            .createQuery("select count(s) " + STORES, Long.class)
            .setParameter("personId", personId)
            .getSingleResult();
    return new PageImpl<>(content, page, count);
  }

  public String storeName(Long personId, Long storeId) {
    return entityManager
        .createQuery("select s.name " + STORES + " and s.id = :storeId", String.class)
        .setParameter("personId", personId)
        .setParameter("storeId", storeId)
        .getResultStream()
        .findFirst()
        .orElseThrow(() -> new NotFoundException("報酬明細の店舗が見つかりません"));
  }
}
