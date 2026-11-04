package com.kizuna.order.contact;

import com.kizuna.order.domain.OrderApplicationRepository;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.shared.exception.NotFoundException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SourceCustomers {
  private final OrderRepository orders;
  private final OrderApplicationRepository applications;

  /** 受注に選択された顧客。顧客未設定なら空、受注が無ければ NotFoundException。 */
  @Transactional(readOnly = true)
  public Optional<String> ofOrder(Long storeId, String orderId) {
    return Optional.ofNullable(
        orders
            .findByIdAndStoreId(orderId, storeId)
            .orElseThrow(() -> new NotFoundException("受注が見つかりません"))
            .getCustomerId());
  }

  /** 申請は確認後の受注を通してだけ顧客に届く。確認前のゲストの同意原文は顧客台帳ではない。 */
  @Transactional(readOnly = true)
  public Optional<String> ofApplication(Long storeId, String applicationId) {
    var application =
        applications
            .findByIdAndStoreId(applicationId, storeId)
            .orElseThrow(() -> new NotFoundException("予約申請が見つかりません"));
    return application.getOrderId() == null
        ? Optional.empty()
        : ofOrder(storeId, application.getOrderId());
  }
}
