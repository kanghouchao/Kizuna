package com.kizuna.order.infrastructure;

import com.kizuna.order.domain.RemunerationStoreRow;
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

  /** 店舗を跨ぐ本人照会。境界は本人 id（cast_id）で、在籍の歴史がある店舗だけを返す。 */
  public Page<RemunerationStoreRow> stores(Long personId, Pageable page) {
    var content =
        entityManager
            .createQuery(
                "select new com.kizuna.order.domain.RemunerationStoreRow(s.id, s.name) "
                    + STORES
                    + " order by s.id asc",
                RemunerationStoreRow.class)
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

  /** 本人に在籍の歴史が無い店舗は 404。本人向けの集計はこれで店舗を確かめてから、本人・店舗を明示して引く。 */
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
