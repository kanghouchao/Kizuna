package com.kizuna.order.application;

import com.kizuna.order.api.dto.MonthlyRemunerationCastSummary;
import com.kizuna.order.api.dto.PlatformMonthlyRemunerationResponse;
import com.kizuna.order.api.dto.PlatformMonthlyRemunerationStoreSummary;
import com.kizuna.order.infrastructure.PlatformMonthlyRemunerationStores;
import com.kizuna.order.infrastructure.RemunerationQuery;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.shared.storescope.StoreSetScoped;
import com.kizuna.shared.web.CursorPage;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformMonthlyRemunerationService {
  private final RemunerationQuery query;
  private final PlatformMonthlyRemunerationStores stores;

  @StoreSetScoped
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page<PlatformMonthlyRemunerationStoreSummary> stores(String search, int page, int size) {
    var scope = scope();
    return stores.search(scope, search, page(page, size));
  }

  @StoreSetScoped
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page<MonthlyRemunerationCastSummary> casts(
      Long storeId, String search, int page, int size) {
    var scope = scope();
    var pageable = page(page, size);
    requireStore(scope, storeId);
    return query.casts(storeId, search, pageable);
  }

  @StoreSetScoped
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PlatformMonthlyRemunerationResponse monthly(
      Long storeId, Long personId, String month, int page, int size) {
    var scope = scope();
    var pageable = page(page, size);
    if (personId <= 0) throw new ServiceException("キャスト本人の指定が不正です");
    if (!month.matches("[0-9]{4}-(0[1-9]|1[0-2])") || month.startsWith("0000"))
      throw new ServiceException("対象月は YYYY-MM 形式で指定してください");
    var target = YearMonth.parse(month);
    String storeName = requireStore(scope, storeId);
    String name = query.personName(storeId, personId);
    return new PlatformMonthlyRemunerationResponse(
        storeId,
        storeName,
        personId,
        name,
        month,
        query.total(storeId, personId, target),
        query.orders(storeId, personId, target, pageable));
  }

  private StoreScope scope() {
    var scope =
        StoreScope.fromAuthentication(SecurityContextHolder.getContext().getAuthentication());
    if (scope == null) throw new AccessDeniedException("授権店舗集合を解決できません");
    return scope;
  }

  private String requireStore(StoreScope scope, Long storeId) {
    if (storeId <= 0) throw new ServiceException("店舗の指定が不正です");
    if (!scope.authorizes(storeId)) throw new AccessDeniedException("授権店舗集合に含まれません");
    return stores.name(storeId);
  }

  private PageRequest page(int page, int size) {
    int limit = CursorPage.clampSize(size);
    if (page < 0 || (long) page * limit > Integer.MAX_VALUE)
      throw new ServiceException("ページ番号が不正です");
    return PageRequest.of(page, limit);
  }
}
