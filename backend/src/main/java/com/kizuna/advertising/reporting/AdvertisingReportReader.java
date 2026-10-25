package com.kizuna.advertising.reporting;

import com.kizuna.advertising.domain.AdvertisingCategory;
import com.kizuna.shared.exception.ServiceUnavailableException;
import com.kizuna.shared.storescope.StoreScope;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdvertisingReportReader {
  private final EntityManager em;

  public record Cost(
      Long storeId,
      String id,
      Long version,
      String month,
      AdvertisingCategory category,
      Integer amount) {}

  // 店舗経路での担当者／横断照会者と広告費権限の対は呼び出す端点が判定する。ここは店舗経路の下限だけを持つ。
  @PreAuthorize("hasAnyAuthority('PERM_ADVERTISING_COST_VIEW', 'PERM_ADVERTISING_COST_SET_VIEW')")
  @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
  public AdvertisingReportFacts store(
      Long storeId, List<Long> stores, String from, String to, int limit) {
    return read(new StoreScope(false, Set.of(storeId)), stores, from, to, limit);
  }

  @PreAuthorize("hasAuthority('PERM_ADVERTISING_COST_SET_VIEW')")
  @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
  public AdvertisingReportFacts groupWide(
      StoreScope scope, List<Long> stores, String from, String to, int limit) {
    if (scope == null) throw new AccessDeniedException("授権店舗集合を解決できません");
    return read(scope, stores, from, to, limit);
  }

  private AdvertisingReportFacts read(
      StoreScope scope, List<Long> stores, String from, String to, int limit) {
    for (var store : stores)
      if (!scope.authorizes(store)) throw new AccessDeniedException("授権店舗集合に含まれません");
    if (limit < 1 || limit > 100_000) throw tooLarge();
    if (stores.isEmpty()) return new AdvertisingReportFacts(List.of());
    var costs =
        em.createQuery(
                """
        select new com.kizuna.advertising.reporting.AdvertisingReportReader$Cost(
          c.storeId, c.id, c.version, c.month, c.category, c.amount)
        from com.kizuna.advertising.domain.AdvertisingCost c
        where c.storeId in :stores and c.month >= :from and c.month <= :to and c.deleted=false
        order by c.storeId, c.month, c.id
        """,
                Cost.class)
            .setParameter("stores", stores)
            .setParameter("from", from)
            .setParameter("to", to)
            .setMaxResults(limit + 1)
            .getResultList();
    if (costs.size() > limit) throw tooLarge();
    if (costs.stream().anyMatch(c -> c.amount() == null || c.amount() < 0 || c.category() == null))
      throw new ServiceUnavailableException("広告費を確認できません。管理者へお問い合わせください");
    return new AdvertisingReportFacts(
        costs.stream()
            .map(
                c ->
                    new AdvertisingReportFacts.Cost(
                        c.storeId(),
                        c.id(),
                        c.version(),
                        c.month(),
                        c.category().name(),
                        c.amount()))
            .toList());
  }

  private static ServiceUnavailableException tooLarge() {
    return new ServiceUnavailableException("広告費根拠が多すぎます。期間または店舗を絞ってください");
  }
}
