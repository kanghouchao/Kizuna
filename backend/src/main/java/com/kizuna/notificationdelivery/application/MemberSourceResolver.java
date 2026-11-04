package com.kizuna.notificationdelivery.application;

import com.kizuna.customer.domain.ContactPurpose;
import com.kizuna.customer.domain.ContactType;
import com.kizuna.membernotification.contact.MemberContacts;
import com.kizuna.notificationdelivery.domain.DeliveryPurpose;
import com.kizuna.notificationdelivery.domain.DeliverySourceType;
import com.kizuna.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 会員宛はグループ級の行なので店舗を読まない。会員の宛先はログイン用メールアドレスだけで、他のチャネルの宛先を持たない。 */
@Component
@RequiredArgsConstructor
public class MemberSourceResolver implements DeliverySourceResolver {
  private final MemberContacts members;

  @Override
  public DeliverySourceType sourceType() {
    return DeliverySourceType.MEMBER;
  }

  @Override
  public DeliveryRecipient resolve(
      Long storeId, String sourceId, ContactType type, DeliveryPurpose purpose) {
    long memberId;
    try {
      memberId = Long.parseLong(sourceId);
    } catch (NumberFormatException e) {
      throw new NotFoundException("会員が見つかりません");
    }
    var contact =
        members.resolve(
            memberId,
            switch (purpose) {
              case BUSINESS -> ContactPurpose.BUSINESS;
              case MARKETING -> ContactPurpose.MARKETING;
            });
    return type == ContactType.EMAIL && contact.allowed()
        ? new DeliveryRecipient(contact.address(), ContactDecision.ALLOWED)
        : new DeliveryRecipient(null, ContactDecision.NOT_ALLOWED);
  }
}
