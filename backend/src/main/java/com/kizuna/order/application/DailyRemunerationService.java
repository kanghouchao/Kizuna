package com.kizuna.order.application;

import com.kizuna.cast.domain.CastRepository;
import com.kizuna.order.infrastructure.RemunerationQuery;
import com.kizuna.order.infrastructure.SelfMonthlyRemunerationQuery;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.user.application.ActorIdentityService;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DailyRemunerationService {
  private final RemunerationQuery query;
  private final ActorIdentityService actors;
  private final CastRepository people;
  private final SelfMonthlyRemunerationQuery selfQuery;

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public DailyRemuneration store(
      Long storeId, Long personId, String businessDate, Pageable pageable) {
    var target = day(businessDate);
    if (personId <= 0) throw new ServiceException("キャスト本人の指定が不正です");
    String name = query.personName(storeId, personId);
    return new DailyRemuneration(
        personId,
        name,
        target,
        query.dailyTotal(storeId, personId, target),
        query.dailyOrders(storeId, personId, target, pageable));
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public SelfDailyRemuneration self(
      String actor, Long storeId, String businessDate, Pageable pageable) {
    var target = day(businessDate);
    if (storeId <= 0) throw new ServiceException("店舗の指定が不正です");
    var person =
        people
            .findByUserId(actors.requireUserId(actor))
            .orElseThrow(() -> new NotFoundException("報酬明細の店舗が見つかりません"));
    Long personId = person.getId();
    String name = selfQuery.storeName(personId, storeId);
    return new SelfDailyRemuneration(
        storeId,
        name,
        target,
        query.dailyTotal(storeId, personId, target),
        query.dailyOrders(storeId, personId, target, pageable));
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
}
