package com.kizuna.order.application;

import java.time.OffsetDateTime;

/** 受注 1 件の帰属の現況。帰属記録が 1 件も無い受注は {@link #none()}。会員側は会員コードだけを持つ。 */
public record OrderAttributionState(
    Long id,
    boolean attributed,
    String memberCode,
    String source,
    OffsetDateTime attributedAt,
    String invalidatedReason,
    OffsetDateTime invalidatedAt) {

  public static OrderAttributionState none() {
    return new OrderAttributionState(null, false, null, null, null, null, null);
  }
}
