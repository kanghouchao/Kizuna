package com.kizuna.remuneration.infrastructure;

import com.kizuna.remuneration.domain.BonusAward;
import com.kizuna.remuneration.domain.GuaranteeTerm;
import com.kizuna.remuneration.domain.GuaranteeTimeline;
import com.kizuna.remuneration.domain.RemunerationAmounts;
import com.kizuna.remuneration.domain.RemunerationChange;
import com.kizuna.remuneration.domain.RemunerationRequest;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
public class RemunerationRecords {
  private final EntityManager em;

  public void persist(Object entity) {
    em.persist(entity);
  }

  public void flush() {
    em.flush();
  }

  public void lock(String key) {
    // 初回作成の行が無い場合も同じ店舗・本人の変更を直列化する。衝突は待機を増やすだけである。
    em.createNativeQuery("select pg_advisory_xact_lock(387, :key)", Object.class)
        .setParameter("key", key.hashCode())
        .getSingleResult();
  }

  public Optional<RemunerationRequest> receipt(Long store, Long actor, UUID request) {
    return em.createQuery(
            "select r from RemunerationRequest r where r.storeId=:store and r.actorId=:actor and r.requestId=:request",
            RemunerationRequest.class)
        .setParameter("store", store)
        .setParameter("actor", actor)
        .setParameter("request", request)
        .getResultStream()
        .findFirst();
  }

  public Optional<GuaranteeTimeline> timeline(Long store, Long person) {
    return em.createQuery(
            "select t from GuaranteeTimeline t where t.storeId=:store and t.personId=:person",
            GuaranteeTimeline.class)
        .setParameter("store", store)
        .setParameter("person", person)
        .getResultStream()
        .findFirst();
  }

  public GuaranteeTimeline lockedTimeline(Long store, Long person) {
    lock("timeline:" + store + ":" + person);
    return timeline(store, person)
        .map(
            t -> {
              em.refresh(t, LockModeType.PESSIMISTIC_WRITE);
              return t;
            })
        .orElseGet(
            () -> {
              var t = GuaranteeTimeline.builder().personId(person).build();
              persist(t);
              return t;
            });
  }

  public List<GuaranteeTerm> activeTerms(Long store, Long person) {
    return em.createQuery(
            "select t from GuaranteeTerm t where t.storeId=:store and t.personId=:person and t.cancelledAt is null order by t.effectiveFrom, t.id",
            GuaranteeTerm.class)
        .setParameter("store", store)
        .setParameter("person", person)
        .getResultList();
  }

  public Page<GuaranteeTerm> terms(Long store, Long person, Pageable page) {
    var rows =
        em.createQuery(
                "select t from GuaranteeTerm t where t.storeId=:store and t.personId=:person order by t.effectiveFrom desc, t.id desc",
                GuaranteeTerm.class)
            .setParameter("store", store)
            .setParameter("person", person)
            .setFirstResult((int) page.getOffset())
            .setMaxResults(page.getPageSize())
            .getResultList();
    long total =
        em.createQuery(
                "select count(t) from GuaranteeTerm t where t.storeId=:store and t.personId=:person",
                Long.class)
            .setParameter("store", store)
            .setParameter("person", person)
            .getSingleResult();
    return new PageImpl<>(rows, page, total);
  }

  public GuaranteeTerm term(Long store, String id) {
    return em.createQuery(
            "select t from GuaranteeTerm t where t.storeId=:store and t.id=:id",
            GuaranteeTerm.class)
        .setParameter("store", store)
        .setParameter("id", id)
        .getResultStream()
        .findFirst()
        .orElseThrow(() -> new NotFoundException("保証条件が見つかりません"));
  }

  public BonusAward bonus(Long store, String id) {
    return em.createQuery(
            "select b from BonusAward b where b.storeId=:store and b.id=:id", BonusAward.class)
        .setParameter("store", store)
        .setParameter("id", id)
        .getResultStream()
        .findFirst()
        .orElseThrow(() -> new NotFoundException("ボーナスが見つかりません"));
  }

  public void refreshForWrite(Object entity) {
    em.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
  }

  public Page<BonusAward> bonuses(
      Long store, Long person, LocalDate start, LocalDate end, Pageable page) {
    String where =
        " from BonusAward b where b.storeId=:store and b.personId=:person and b.awardDate>=:start and b.awardDate<=:end";
    var rows =
        em.createQuery(
                "select b" + where + " order by b.awardDate desc, b.id desc", BonusAward.class)
            .setParameter("store", store)
            .setParameter("person", person)
            .setParameter("start", start)
            .setParameter("end", end)
            .setFirstResult((int) page.getOffset())
            .setMaxResults(page.getPageSize())
            .getResultList();
    long total =
        em.createQuery("select count(b)" + where, Long.class)
            .setParameter("store", store)
            .setParameter("person", person)
            .setParameter("start", start)
            .setParameter("end", end)
            .getSingleResult();
    return new PageImpl<>(rows, page, total);
  }

  public record BonusDay(LocalDate day, BigDecimal amount) {
    public long checkedAmount() {
      try {
        return RemunerationAmounts.require(amount.longValueExact());
      } catch (ArithmeticException e) {
        throw new ServiceException("ボーナス合計が扱える金額の範囲を超えています");
      }
    }
  }

  public List<BonusDay> bonusDays(Long store, Long person, LocalDate start, LocalDate end) {
    return em.createQuery(
            "select new com.kizuna.remuneration.infrastructure.RemunerationRecords$BonusDay(b.awardDate, sum(cast(b.amount as BigDecimal))) from BonusAward b where b.storeId=:store and b.personId=:person and b.cancelledAt is null and b.awardDate>=:start and b.awardDate<=:end group by b.awardDate",
            BonusDay.class)
        .setParameter("store", store)
        .setParameter("person", person)
        .setParameter("start", start)
        .setParameter("end", end)
        .getResultList();
  }

  public List<RemunerationChange> changes(
      Long store, String type, String id, String cursor, int size) {
    String where =
        " from RemunerationChange c where c.storeId=:store and c.resourceType=:type and c.resourceId=:id";
    OffsetDateTime at = null;
    String last = null;
    if (cursor != null) {
      try {
        int split = cursor.lastIndexOf('|');
        at = OffsetDateTime.parse(cursor.substring(0, split));
        last = cursor.substring(split + 1);
        if (!last.matches("[0-9]+")) throw new IllegalArgumentException();
      } catch (RuntimeException e) {
        throw new ServiceException("履歴のカーソルが不正です");
      }
      where += " and (c.createdAt<:at or (c.createdAt=:at and c.id<:last))";
    }
    var q =
        em.createQuery(
                "select c" + where + " order by c.createdAt desc,c.id desc",
                RemunerationChange.class)
            .setParameter("store", store)
            .setParameter("type", type)
            .setParameter("id", id);
    if (at != null) q.setParameter("at", at).setParameter("last", last);
    return q.setMaxResults(size + 1).getResultList();
  }
}
