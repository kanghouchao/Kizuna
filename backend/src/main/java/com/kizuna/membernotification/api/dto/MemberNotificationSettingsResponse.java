package com.kizuna.membernotification.api.dto;

import com.kizuna.customer.domain.ContactPermissionStatus;
import com.kizuna.membernotification.application.MemberNotificationSettings;

/** 会員の通知設定の実効値（明示の選択、無ければ既定）。 */
public record MemberNotificationSettingsResponse(
    ContactPermissionStatus businessStatus, ContactPermissionStatus marketingStatus) {
  public static MemberNotificationSettingsResponse of(MemberNotificationSettings settings) {
    return new MemberNotificationSettingsResponse(settings.business(), settings.marketing());
  }
}
