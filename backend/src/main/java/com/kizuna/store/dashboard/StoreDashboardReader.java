package com.kizuna.store.dashboard;

import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.store.domain.StoreStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** ダッシュボードの区画が数える店舗の読み取り。呼び出し者の店舗集合で絞り、空集合はどの店舗も数えない（全店と読み替えない）。 権限は区画の端点が守り、ここは店舗の管理の権限を求めない。 */
@Service
@RequiredArgsConstructor
public class StoreDashboardReader {
  private final EntityManager em;

  @Transactional(readOnly = true)
  public StoreStatusCounts statusCounts(StoreScope scope) {
    if (scope.storeIds().isEmpty()) return StoreStatusCounts.NONE;
    var counts = new EnumMap<StoreStatus, Long>(StoreStatus.class);
    em.createQuery(
            """
            select s.status, count(s) from com.kizuna.store.domain.Store s
            where s.id in :stores
            group by s.status
            """,
            Tuple.class)
        .setParameter("stores", scope.storeIds())
        .getResultList()
        .forEach(row -> counts.put(row.get(0, StoreStatus.class), row.get(1, Long.class)));
    long preparing = counts.getOrDefault(StoreStatus.PREPARING, 0L);
    long active = counts.getOrDefault(StoreStatus.ACTIVE, 0L);
    return new StoreStatusCounts(preparing + active, preparing, active);
  }

  @Transactional(readOnly = true)
  public Map<Long, StoreStatus> statuses(StoreScope scope) {
    var statuses = new HashMap<Long, StoreStatus>();
    if (scope.storeIds().isEmpty()) return statuses;
    em.createQuery(
            "select s.id, s.status from com.kizuna.store.domain.Store s where s.id in :stores",
            Tuple.class)
        .setParameter("stores", scope.storeIds())
        .getResultList()
        .forEach(row -> statuses.put(row.get(0, Long.class), row.get(1, StoreStatus.class)));
    return statuses;
  }
}
