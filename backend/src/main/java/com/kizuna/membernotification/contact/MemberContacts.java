package com.kizuna.membernotification.contact;

import com.kizuna.customer.domain.ContactPermissionStatus;
import com.kizuna.customer.domain.ContactPurpose;
import com.kizuna.member.application.MemberLoginAddress;
import com.kizuna.membernotification.application.MemberNotificationSettingService;
import com.kizuna.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 会員宛の通知の宛先と、その用途での連絡可否。宛先はログイン用メールアドレス、可否は本人の通知設定の実効値だけで決める。 */
@Service
@RequiredArgsConstructor
public class MemberContacts {
  private final MemberLoginAddress addresses;
  private final MemberNotificationSettingService settings;

  public record Contact(String address, boolean allowed) {}

  /** 会員が存在しなければ NotFoundException。退会済みの会員へは送らない。 */
  @Transactional(readOnly = true)
  public Contact resolve(long memberId, ContactPurpose purpose) {
    var address = addresses.of(memberId).orElseThrow(() -> new NotFoundException("会員が見つかりません"));
    if (address.withdrawn()) return new Contact(null, false);
    return new Contact(
        address.email(), settings.effective(memberId, purpose) == ContactPermissionStatus.ALLOWED);
  }
}
