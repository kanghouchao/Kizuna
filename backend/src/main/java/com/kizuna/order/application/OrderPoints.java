package com.kizuna.order.application;

import com.kizuna.point.application.PointLedgerService.PointQuote;

/** 受注の見込みに載せるポイント。会員へ紐づかない受注は残高と会員コードを持たず、付与予定も 0。 */
public record OrderPoints(
    boolean memberLinked,
    Long pointBalance,
    String memberCode,
    int usageUnit,
    int usePoints,
    int grantPoints) {

  static OrderPoints nonMember(int usageUnit, int usePoints) {
    return new OrderPoints(false, null, null, usageUnit, usePoints, 0);
  }

  static OrderPoints member(String memberCode, int usePoints, PointQuote quote) {
    return new OrderPoints(
        true, quote.balance(), memberCode, quote.usageUnit(), usePoints, quote.grantPoints());
  }
}
