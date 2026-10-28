package com.kizuna.order.application;

import com.kizuna.customer.domain.ContactPermissionStatus;
import com.kizuna.customer.domain.ContactType;

/** 今回の連絡可否の記録。API 経由でも申請の確定からでも同じ型で受け、長さと必須は {@link BusinessContactPermissions} が検める。 */
public record BusinessContactPermissionInput(
    ContactType type, ContactPermissionStatus status, String source, String reason) {}
