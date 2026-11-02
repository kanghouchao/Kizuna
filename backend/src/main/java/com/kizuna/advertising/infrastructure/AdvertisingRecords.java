package com.kizuna.advertising.infrastructure;

import com.kizuna.advertising.domain.AdvertisingChange;
import com.kizuna.advertising.domain.AdvertisingCost;
import com.kizuna.advertising.domain.AdvertisingMonth;
import com.kizuna.shared.exception.NotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdvertisingRecords {
  private final EntityManager em;

  public void persist(Object entity) {
    em.persist(entity);
  }

  public void flush() {
    em.flush();
  }

  public void lockMonth(Long store, String month) {
    // 未作成の月も、同じ論理キーで作成前から直列化する。
    em.createNativeQuery("select pg_advisory_xact_lock(389,:key)", Object.class)
        .setParameter("key", ("month:" + store + ":" + month).hashCode())
        .getSingleResult();
  }

  public Optional<AdvertisingMonth> month(Long store, String month) {
    return em.createQuery(
            "select m from AdvertisingMonth m where m.storeId=:store and m.month=:month",
            AdvertisingMonth.class)
        .setParameter("store", store)
        .setParameter("month", month)
        .getResultStream()
        .findFirst();
  }

  public AdvertisingMonth advance(Long store, String month) {
    var value =
        month(store, month)
            .orElseGet(
                () -> {
                  var m = AdvertisingMonth.builder().month(month).build();
                  m.setStoreId(store);
                  persist(m);
                  return m;
                });
    value.advance();
    return value;
  }

  public AdvertisingCost cost(Long store, String id) {
    return em.createQuery(
            "select c from AdvertisingCost c where c.storeId=:store and c.id=:id and c.deleted=false",
            AdvertisingCost.class)
        .setParameter("store", store)
        .setParameter("id", id)
        .getResultStream()
        .findFirst()
        .orElseThrow(() -> new NotFoundException("広告費が見つかりません"));
  }

  /** 論理削除の有無を問わず読む。受領の対象の現在の状態を返すためにだけ使う。 */
  public Optional<AdvertisingCost> recorded(Long store, String id) {
    return em.createQuery(
            "select c from AdvertisingCost c where c.storeId=:store and c.id=:id",
            AdvertisingCost.class)
        .setParameter("store", store)
        .setParameter("id", id)
        .getResultStream()
        .findFirst();
  }

  public AdvertisingCost refresh(AdvertisingCost cost) {
    em.refresh(cost, LockModeType.PESSIMISTIC_WRITE);
    if (cost.isDeleted()) throw new NotFoundException("広告費が見つかりません");
    return cost;
  }

  public Page<AdvertisingCost> list(Long store, String month, Pageable page) {
    var q =
        em.createQuery(
                "select c from AdvertisingCost c where c.storeId=:store and c.month=:month and c.deleted=false order by c.id",
                AdvertisingCost.class)
            .setParameter("store", store)
            .setParameter("month", month);
    return new PageImpl<>(
        q.setFirstResult((int) page.getOffset()).setMaxResults(page.getPageSize()).getResultList(),
        page,
        count(store, month));
  }

  public long count(Long store, String month) {
    return em.createQuery(
            "select count(c) from AdvertisingCost c where c.storeId=:store and c.month=:month and c.deleted=false",
            Long.class)
        .setParameter("store", store)
        .setParameter("month", month)
        .getSingleResult();
  }

  public List<AdvertisingCost> all(Long store, String month, int max) {
    return em.createQuery(
            "select c from AdvertisingCost c where c.storeId=:store and c.month=:month and c.deleted=false order by c.id",
            AdvertisingCost.class)
        .setParameter("store", store)
        .setParameter("month", month)
        .setMaxResults(max + 1)
        .getResultList();
  }

  public List<Object[]> totals(Long store, String month) {
    return em.createQuery(
            "select c.category, sum(c.amount), count(c) from AdvertisingCost c where c.storeId=:store and c.month=:month and c.deleted=false group by c.category",
            Object[].class)
        .setParameter("store", store)
        .setParameter("month", month)
        .getResultList();
  }

  public AdvertisingChange change(Long store, String month, String id) {
    return em.createQuery(
            "select c from AdvertisingChange c where c.storeId=:store and c.month=:month and c.id=:id",
            AdvertisingChange.class)
        .setParameter("store", store)
        .setParameter("month", month)
        .setParameter("id", id)
        .getResultStream()
        .findFirst()
        .orElseThrow(() -> new NotFoundException("変更履歴が見つかりません"));
  }
}
