package com.kizuna.order.application;

import com.kizuna.order.domain.RemunerationCastRow;
import com.kizuna.order.infrastructure.RemunerationQuery;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.web.CursorPage;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MonthlyRemunerationService {
  private final RemunerationQuery query;

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page<RemunerationCastRow> casts(Long storeId, String search, int page, int size) {
    return query.casts(storeId, search, page(page, size));
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public MonthlyRemuneration monthly(
      Long storeId, Long personId, String month, int page, int size) {
    var pageable = page(page, size);
    if (personId <= 0) throw new ServiceException("キャスト本人の指定が不正です");
    YearMonth target;
    try {
      if (!month.matches("[0-9]{4}-(0[1-9]|1[0-2])") || month.startsWith("0000"))
        throw new ServiceException("対象月は YYYY-MM 形式で指定してください");
      target = YearMonth.parse(month);
    } catch (DateTimeParseException ex) {
      throw new ServiceException("対象月は YYYY-MM 形式で指定してください");
    }
    String name = query.personName(storeId, personId);
    return new MonthlyRemuneration(
        personId,
        name,
        month,
        query.total(storeId, personId, target),
        query.orders(storeId, personId, target, pageable));
  }

  private PageRequest page(int page, int size) {
    if (page < 0 || (long) page * CursorPage.clampSize(size) > Integer.MAX_VALUE)
      throw new ServiceException("ページ番号が不正です");
    return PageRequest.of(page, CursorPage.clampSize(size));
  }
}
