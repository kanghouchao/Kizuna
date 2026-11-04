package com.kizuna.membernotification.application;

import com.kizuna.customer.domain.ContactPermissionStatus;

/** 会員の通知設定の実効値。用途ごとに明示の選択、無ければ既定。 */
public record MemberNotificationSettings(
    ContactPermissionStatus business, ContactPermissionStatus marketing) {}
