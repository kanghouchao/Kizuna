package com.kizuna.order.application;

import com.kizuna.cast.domain.CastRepository;
import com.kizuna.order.api.dto.DailyRemunerationResponse;
import com.kizuna.order.api.dto.SelfDailyRemunerationResponse;
import com.kizuna.order.api.dto.SelfMonthlyRemunerationOrderSummary;
import com.kizuna.order.infrastructure.RemunerationQuery;
import com.kizuna.order.infrastructure.SelfMonthlyRemunerationQuery;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScopeExempt;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.user.application.ActorIdentityService;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DailyRemunerationService {
  private final RemunerationQuery query;
  private final StoreContext storeContext;
  private final ActorIdentityService actors;
  private final CastRepository people;
  private final SelfMonthlyRemunerationQuery selfQuery;

  @StoreScoped
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public DailyRemunerationResponse store(Long personId, String businessDate, int page, int size) {
    var target = day(businessDate);
    var pageable = page(page, size);
    if (personId <= 0) throw new ServiceException("キャスト本人の指定が不正です");
    Long storeId = storeContext.getStoreId();
    String name = query.personName(storeId, personId);
    return new DailyRemunerationResponse(
        personId,
        name,
        target,
        query.dailyTotal(storeId, personId, target),
        query.dailyOrders(storeId, personId, target, pageable));
  }

  @StoreScopeExempt(reason = "認証主体の本人と対象店舗の歴史在籍を照合し、退店を含む本人の受注だけを集計する")
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public SelfDailyRemunerationResponse self(
      String actor, Long storeId, String businessDate, int page, int size) {
    var target = day(businessDate);
    var pageable = page(page, size);
    if (storeId <= 0) throw new ServiceException("店舗の指定が不正です");
    var person =
        people
            .findByPlatformUserId(actors.requireUserId(actor))
            .orElseThrow(() -> new NotFoundException("報酬明細の店舗が見つかりません"));
    Long personId = person.getId();
    String name = selfQuery.storeName(personId, storeId);
    return new SelfDailyRemunerationResponse(
        storeId,
        name,
        target,
        query.dailyTotal(storeId, personId, target),
        query
            .dailyOrders(storeId, personId, target, pageable)
            .map(
                row ->
                    new SelfMonthlyRemunerationOrderSummary(
                        row.orderId(),
                        row.businessDate(),
                        row.serviceSummary(),
                        row.accruedRemuneration(),
                        row.completionInvalidated())));
  }

  private LocalDate day(String value) {
    try {
      if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || value.startsWith("0000"))
        throw new ServiceException("営業日は YYYY-MM-DD 形式の有効な日付で指定してください");
      return LocalDate.parse(value);
    } catch (DateTimeParseException ex) {
      throw new ServiceException("営業日は YYYY-MM-DD 形式の有効な日付で指定してください");
    }
  }

  private PageRequest page(int page, int size) {
    int effectiveSize = CursorPage.clampSize(size);
    if (page < 0 || (long) page * effectiveSize > Integer.MAX_VALUE)
      throw new ServiceException("ページ番号が不正です");
    return PageRequest.of(page, effectiveSize);
  }
}
