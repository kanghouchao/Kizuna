package com.kizuna.order.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 注文の部分更新コマンド。null のフィールドは「変更しない」を意味する。
 *
 * <p>会計金額・利用ポイント・自動付与ポイントは含まない — 完了処理だけが確定させる。連絡先の写しも含まない（顧客が着いた受注では撥ねる必要があり、「変更しない」と区別できる
 * 訂正の経路が別に要る）。
 */
public record OrderPatch(
    LocalDate businessDate,
    LocalTime arrivalScheduledStartTime,
    LocalTime arrivalScheduledEndTime,
    Integer pax,
    Integer courseMinutes,
    Integer extensionMinutes,
    List<String> optionCodes,
    String discountName,
    Integer manualDiscount,
    String locationAddress,
    String locationBuilding,
    String carrier,
    String mediaName,
    String remarks,
    String castDriverMessage) {}
