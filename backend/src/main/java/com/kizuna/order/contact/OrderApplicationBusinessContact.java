package com.kizuna.order.contact;

import com.kizuna.customer.domain.ContactType;
import com.kizuna.order.application.GuestApplicationConsent;
import com.kizuna.order.domain.OrderApplicationRepository;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScoped;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderApplicationBusinessContact {
  private final OrderApplicationRepository applications;
  private final GuestApplicationConsent consent;
  private final StoreContext store;

  /** 確認前の申請への送信・再送直前に、保存済み証拠と現在の店舗拒否を再判定する。 */
  @StoreScoped
  @Transactional(readOnly = true)
  public OrderBusinessContact.Decision decide(String applicationId, ContactType type) {
    if (!store.hasStoreId()) throw new ServiceException("店舗を指定してください");
    var application =
        applications
            .findById(applicationId)
            .orElseThrow(() -> new NotFoundException("予約申請が見つかりません"));
    return consent.describe(application).stream()
        .filter(p -> p.type() == type)
        .map(p -> new OrderBusinessContact.Decision(p.value(), p.decision()))
        .findFirst()
        .orElse(new OrderBusinessContact.Decision(null, BusinessContactDecision.NOT_ALLOWED));
  }
}
