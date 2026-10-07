package com.kizuna.order.reporting;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.ServiceUnavailableException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shared.storescope.StoreSetScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OperationalReportReader {
  private final EntityManager entityManager;
  private final StoreContext context;
  private final AppProperties properties;
  private final Clock clock;

  @StoreScoped
  @Transactional(
      readOnly = true,
      isolation = Isolation.REPEATABLE_READ,
      timeoutString = "#{@appProperties.operationalReport.readTimeoutSeconds}")
  public OperationalFacts store(LocalDate from, LocalDate to) {
    return read(
        new StoreScope(false, Set.of(context.getStoreId())), context.getStoreId(), from, to);
  }

  @StoreSetScoped
  @Transactional(
      readOnly = true,
      isolation = Isolation.REPEATABLE_READ,
      timeoutString = "#{@appProperties.operationalReport.readTimeoutSeconds}")
  public OperationalFacts platform(Long storeId, LocalDate from, LocalDate to) {
    var scope =
        StoreScope.fromAuthentication(SecurityContextHolder.getContext().getAuthentication());
    if (scope == null) throw new AccessDeniedException("授権店舗集合を解決できません");
    if (storeId != null) {
      if (storeId <= 0) throw new ServiceException("店舗の指定が不正です");
      if (!scope.authorizes(storeId)) throw new AccessDeniedException("授権店舗集合に含まれません");
    }
    return read(scope, storeId, from, to);
  }

  private OperationalFacts read(StoreScope scope, Long storeId, LocalDate from, LocalDate to) {
    var settings = properties.getOperationalReport();
    int maxOrders = settings.getMaxOrders();
    int maxStores = settings.getMaxStores();
    if (maxOrders < 1 || maxOrders > 100_000 || maxStores < 1 || maxStores > 10_000)
      throw new ServiceUnavailableException("帳票件数の設定が範囲外です。管理者へお問い合わせください");
    // Store は行フィルタの対象外なので、無注文店を含む一覧にも授権集合を明示する。
    String storesFrom = "from com.kizuna.store.domain.Store s where 1 = 1";
    if (storeId != null) storesFrom += " and s.id = :storeId";
    else if (!scope.allStores()) storesFrom += " and s.id in :storeIds";
    var storesQuery =
        entityManager.createQuery(
            "select s.id, s.name " + storesFrom + " order by s.id", Tuple.class);
    if (storeId != null) storesQuery.setParameter("storeId", storeId);
    else if (!scope.allStores()) storesQuery.setParameter("storeIds", scope.storeIds());
    var stores =
        storesQuery.setMaxResults(maxStores + 1).getResultList().stream()
            .map(
                row -> new OperationalFacts.Store(row.get(0, Long.class), row.get(1, String.class)))
            .toList();
    if (stores.size() > maxStores) throw tooLarge();
    if (storeId != null && stores.isEmpty()) throw new NotFoundException("店舗が見つかりません");
    if (stores.isEmpty()) return new OperationalFacts(OffsetDateTime.now(clock), stores, List.of());
    String fromClause =
        """
        from com.kizuna.order.domain.Order o
        where o.status = com.kizuna.order.domain.OrderStatus.COMPLETED
          and o.businessDate >= :from and o.businessDate <= :to
          and o.storeId in :storeIds
        """;
    var rows =
        entityManager
            .createQuery(
                """
        select o.id, o.storeId, o.businessDate, o.version, o.completionInvalidated,
          case when o.completionInvalidated then 0 else o.totalFee end,
          case when o.completionInvalidated then 0 else o.accruedRemuneration end
        """
                    + fromClause
                    + " order by o.storeId, o.businessDate, o.id",
                Tuple.class)
            .setParameter("from", from)
            .setParameter("to", to)
            .setParameter("storeIds", stores.stream().map(OperationalFacts.Store::storeId).toList())
            .setMaxResults(maxOrders + 1)
            .getResultList();
    if (rows.size() > maxOrders) throw tooLarge();
    var orders =
        rows.stream()
            .map(
                row ->
                    new OperationalFacts.Order(
                        row.get(0, String.class),
                        row.get(1, Long.class),
                        row.get(2, LocalDate.class),
                        row.get(3, Long.class),
                        row.get(4, Boolean.class),
                        row.get(5, Integer.class),
                        row.get(6, Integer.class)))
            .toList();
    return new OperationalFacts(OffsetDateTime.now(clock), stores, orders);
  }

  private ServiceUnavailableException tooLarge() {
    return new ServiceUnavailableException("対象件数が多いため集計できません。期間または店舗を絞ってください");
  }
}
