package com.kizuna.advertising.infrastructure;

import com.kizuna.advertising.domain.AdvertisingChange;
import com.kizuna.advertising.domain.AdvertisingCost;
import com.kizuna.advertising.domain.AdvertisingMonth;
import com.kizuna.advertising.domain.AdvertisingRequest;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.web.PageCursor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

  public void lock(String key) {
    // 未作成の月と成功受領記録も、同じ論理キーで作成前から直列化する。
    em.createNativeQuery("select pg_advisory_xact_lock(389,:key)", Object.class)
        .setParameter("key", key.hashCode())
        .getSingleResult();
  }

  public void lockMonth(Long store, String month) {
    lock("month:" + store + ":" + month);
  }

  public Optional<AdvertisingMonth> month(String month) {
    return em.createQuery(
            "select m from AdvertisingMonth m where m.month=:month", AdvertisingMonth.class)
        .setParameter("month", month)
        .getResultStream()
        .findFirst();
  }

  public AdvertisingMonth advance(String month) {
    var value =
        month(month)
            .orElseGet(
                () -> {
                  var m = AdvertisingMonth.builder().month(month).build();
                  persist(m);
                  return m;
                });
    value.advance();
    return value;
  }

  public AdvertisingCost cost(String id) {
    return em.createQuery(
            "select c from AdvertisingCost c where c.id=:id and c.deleted=false",
            AdvertisingCost.class)
        .setParameter("id", id)
        .getResultStream()
        .findFirst()
        .orElseThrow(() -> new NotFoundException("広告費が見つかりません"));
  }

  public AdvertisingCost refresh(AdvertisingCost cost) {
    em.refresh(cost, LockModeType.PESSIMISTIC_WRITE);
    if (cost.isDeleted()) throw new NotFoundException("広告費が見つかりません");
    return cost;
  }

  public Page<AdvertisingCost> list(String month, Pageable page) {
    var q =
        em.createQuery(
                "select c from AdvertisingCost c where c.month=:month and c.deleted=false order by c.id",
                AdvertisingCost.class)
            .setParameter("month", month);
    return new PageImpl<>(
        q.setFirstResult((int) page.getOffset()).setMaxResults(page.getPageSize()).getResultList(),
        page,
        count(month));
  }

  public long count(String month) {
    return em.createQuery(
            "select count(c) from AdvertisingCost c where c.month=:month and c.deleted=false",
            Long.class)
        .setParameter("month", month)
        .getSingleResult();
  }

  public List<AdvertisingCost> all(String month, int max) {
    return em.createQuery(
            "select c from AdvertisingCost c where c.month=:month and c.deleted=false order by c.id",
            AdvertisingCost.class)
        .setParameter("month", month)
        .setMaxResults(max + 1)
        .getResultList();
  }

  public List<Object[]> totals(String month) {
    return em.createQuery(
            "select c.category, sum(c.amount), count(c) from AdvertisingCost c where c.month=:month and c.deleted=false group by c.category",
            Object[].class)
        .setParameter("month", month)
        .getResultList();
  }

  public Optional<AdvertisingRequest> receipt(Long actor, UUID request) {
    return em.createQuery(
            "select r from AdvertisingRequest r where r.actorId=:actor and r.requestId=:request",
            AdvertisingRequest.class)
        .setParameter("actor", actor)
        .setParameter("request", request)
        .getResultStream()
        .findFirst();
  }

  public List<AdvertisingChange> changes(String month, String cursor, int size) {
    var after = cursor == null ? null : PageCursor.decode(cursor);
    String extra = after == null ? "" : " and (c.createdAt<:at or (c.createdAt=:at and c.id<:id))";
    var q =
        em.createQuery(
                "select c from AdvertisingChange c where c.month=:month"
                    + extra
                    + " order by c.createdAt desc,c.id desc",
                AdvertisingChange.class)
            .setParameter("month", month);
    if (after != null) q.setParameter("at", after.timestampKey()).setParameter("id", after.id());
    return q.setMaxResults(size + 1).getResultList();
  }

  public AdvertisingChange change(String month, String id) {
    return em.createQuery(
            "select c from AdvertisingChange c where c.month=:month and c.id=:id",
            AdvertisingChange.class)
        .setParameter("month", month)
        .setParameter("id", id)
        .getResultStream()
        .findFirst()
        .orElseThrow(() -> new NotFoundException("変更履歴が見つかりません"));
  }
}
