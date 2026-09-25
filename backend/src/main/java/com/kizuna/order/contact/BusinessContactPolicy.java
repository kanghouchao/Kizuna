package com.kizuna.order.contact;

import com.kizuna.customer.contact.BusinessContactRestrictions;
import com.kizuna.customer.domain.ContactPermissionStatus;
import com.kizuna.customer.domain.ContactType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BusinessContactPolicy {
  private final BusinessContactRestrictions restrictions;

  /** 申請側は保存した宛先と根拠付き状態を渡す。未記録の状態では許可しない。 */
  public BusinessContactDecision evaluate(
      ContactType type, String value, ContactPermissionStatus status) {
    if (restrictions.isDenied(type, value)) return BusinessContactDecision.STORE_DENIED;
    return status == ContactPermissionStatus.ALLOWED
        ? BusinessContactDecision.ALLOWED
        : BusinessContactDecision.NOT_ALLOWED;
  }
}
