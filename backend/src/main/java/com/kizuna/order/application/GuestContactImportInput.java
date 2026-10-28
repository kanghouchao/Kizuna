package com.kizuna.order.application;

import com.kizuna.customer.domain.ContactType;

/** 申請のゲスト同意から顧客へ取り込む連絡先。 */
public record GuestContactImportInput(
    ContactType type, String contactId, Boolean importMarketingConsent) {}
