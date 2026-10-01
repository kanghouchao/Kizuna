package com.kizuna.order.application;

import com.kizuna.cast.domain.CastRepository;
import com.kizuna.order.api.dto.SelfMonthlyRemunerationOrderSummary;
import com.kizuna.order.api.dto.SelfMonthlyRemunerationResponse;
import com.kizuna.order.api.dto.SelfMonthlyRemunerationStoreSummary;
import com.kizuna.order.infrastructure.MonthlyRemunerationQuery;
import com.kizuna.order.infrastructure.SelfMonthlyRemunerationQuery;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreScopeExempt;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.user.application.ActorIdentityService;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SelfMonthlyRemunerationService {
  private final ActorIdentityService actors;
  private final CastRepository people;
  private final SelfMonthlyRemunerationQuery selfQuery;
  private final MonthlyRemunerationQuery monthlyQuery;

  @StoreScopeExempt(reason = "認証主体の本人 ID に属する全在籍から店舗候補を限定し、退店も含める")
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page<SelfMonthlyRemunerationStoreSummary> stores(String actor, int page, int size) {
    var pageable = page(page, size);
    var person = people.findByPlatformUserId(actors.requireUserId(actor));
    return person
        .map(p -> selfQuery.stores(p.getId(), pageable))
        .orElseGet(() -> Page.empty(pageable));
  }

  @StoreScopeExempt(reason = "認証主体の本人と店舗の歴史在籍を照合してから、その本人・店舗だけを共通集計へ渡す")
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public SelfMonthlyRemunerationResponse monthly(
      String actor, Long storeId, String month, int page, int size) {
    var pageable = page(page, size);
    if (storeId <= 0) throw new ServiceException("店舗の指定が不正です");
    if (!month.matches("[0-9]{4}-(0[1-9]|1[0-2])") || month.startsWith("0000"))
      throw new ServiceException("対象月は YYYY-MM 形式で指定してください");
    var target = YearMonth.parse(month);
    var person =
        people
            .findByPlatformUserId(actors.requireUserId(actor))
            .orElseThrow(() -> new NotFoundException("報酬明細の店舗が見つかりません"));
    Long personId = person.getId();
    String name = selfQuery.storeName(personId, storeId);
    return new SelfMonthlyRemunerationResponse(
        storeId,
        name,
        month,
        monthlyQuery.total(storeId, personId, target),
        monthlyQuery
            .orders(storeId, personId, target, pageable)
            .map(
                row ->
                    new SelfMonthlyRemunerationOrderSummary(
                        row.orderId(),
                        row.businessDate(),
                        row.serviceSummary(),
                        row.accruedRemuneration(),
                        row.completionInvalidated())));
  }

  private PageRequest page(int page, int size) {
    int effectiveSize = CursorPage.clampSize(size);
    if (page < 0 || (long) page * effectiveSize > Integer.MAX_VALUE)
      throw new ServiceException("ページ番号が不正です");
    return PageRequest.of(page, effectiveSize);
  }
}
