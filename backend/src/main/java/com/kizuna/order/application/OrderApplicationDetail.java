package com.kizuna.order.application;

import com.kizuna.customer.contact.GuestContactImport;
import com.kizuna.order.domain.GuestContactConsent;
import java.time.OffsetDateTime;
import java.util.List;

/** 予約申請 1 件の詳細。取り込み済みの連絡先には、申請を処理した実行者と時刻が記録として付く。 */
public record OrderApplicationDetail(
    OrderApplicationItem application,
    GuestContactConsent contactConsent,
    List<BusinessContactPermissionState> businessContactPermissions,
    List<GuestContactImport> contactImports,
    Long processedBy,
    OffsetDateTime processedAt) {}
