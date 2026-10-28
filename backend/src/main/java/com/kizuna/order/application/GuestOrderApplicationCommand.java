package com.kizuna.order.application;

import com.kizuna.order.domain.ContactSnapshot;
import java.time.LocalDate;
import java.time.LocalTime;

/** ゲスト予約申請の入力。連絡先は利用者が入力した原文のまま持ち、正規化は申請の受理側が行う。 */
public record GuestOrderApplicationCommand(
    Consent contactConsent,
    LocalDate businessDate,
    LocalTime arrivalScheduledStartTime,
    Integer pax,
    String remarks,
    ContactSnapshot contactSnapshot) {

  public record Consent(String version, Boolean businessAllowed, Boolean marketingAllowed) {}
}
