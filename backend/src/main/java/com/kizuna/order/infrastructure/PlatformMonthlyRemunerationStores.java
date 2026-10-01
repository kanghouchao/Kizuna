package com.kizuna.order.infrastructure;

import com.kizuna.order.api.dto.PlatformMonthlyRemunerationStoreSummary;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.storescope.StoreScope;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlatformMonthlyRemunerationStores {
  private final EntityManager entityManager;

  public Page<PlatformMonthlyRemunerationStoreSummary> search(
      StoreScope scope, String search, Pageable pageable) {
    String keyword = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
    // Store 自体は店舗フィルタの対象外なので、候補と件数の双方に授権条件を付ける。
    String from = "from com.kizuna.store.domain.Store s where 1 = 1";
    if (!scope.allStores()) from += " and s.id in :storeIds";
    if (!keyword.isEmpty()) from += " and locate(:search, lower(s.name)) > 0";
    var rows =
        entityManager.createQuery(
            "select s.id, s.name " + from + " order by s.id asc", Tuple.class);
    var count = entityManager.createQuery("select count(s) " + from, Long.class);
    if (!scope.allStores()) {
      rows.setParameter("storeIds", scope.storeIds());
      count.setParameter("storeIds", scope.storeIds());
    }
    if (!keyword.isEmpty()) {
      rows.setParameter("search", keyword);
      count.setParameter("search", keyword);
    }
    var content =
        rows
            .setFirstResult((int) pageable.getOffset())
            .setMaxResults(pageable.getPageSize())
            .getResultList()
            .stream()
            .map(
                row ->
                    new PlatformMonthlyRemunerationStoreSummary(
                        row.get(0, Long.class), row.get(1, String.class)))
            .toList();
    return new PageImpl<>(content, pageable, count.getSingleResult());
  }

  public String name(Long storeId) {
    return entityManager
        .createQuery(
            "select s.name from com.kizuna.store.domain.Store s where s.id = :storeId",
            String.class)
        .setParameter("storeId", storeId)
        .getResultStream()
        .findFirst()
        .orElseThrow(() -> new NotFoundException("店舗が見つかりません"));
  }
}
