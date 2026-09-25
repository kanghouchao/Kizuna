package com.kizuna.order.contact;

import com.kizuna.customer.domain.ContactType;
import com.kizuna.order.application.BusinessContactPermissions;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScoped;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderBusinessContact {
  private final OrderRepository orders;
  private final BusinessContactPermissions permissions;
  private final StoreContext storeContext;

  public record Decision(String value, BusinessContactDecision decision) {}

  /** 授権済み店舗文脈で送信・再送の直前に呼び、返された宛先だけへ送信する。 */
  @StoreScoped
  @Transactional(readOnly = true)
  public Decision decide(String orderId, ContactType type) {
    if (!storeContext.hasStoreId()) throw new ServiceException("店舗を指定してください");
    var order = orders.findById(orderId).orElseThrow(() -> new NotFoundException("受注が見つかりません"));
    return permissions.describe(order).stream()
        .filter(p -> p.type() == type)
        .map(p -> new Decision(p.value(), p.decision()))
        .findFirst()
        .orElse(new Decision(null, BusinessContactDecision.NOT_ALLOWED));
  }
}
