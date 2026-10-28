package com.kizuna.order.application;

import com.kizuna.customer.domain.ContactPermissionStatus;
import com.kizuna.customer.domain.ContactType;
import com.kizuna.order.contact.BusinessContactDecision;
import java.time.OffsetDateTime;

/** 連絡先 1 件の現在の連絡可否と、その連絡先へ業務連絡してよいかの判定。 */
public record BusinessContactPermissionState(
    ContactType type,
    String value,
    ContactPermissionStatus status,
    String source,
    String reason,
    Long recordedBy,
    OffsetDateTime recordedAt,
    BusinessContactDecision decision) {}
