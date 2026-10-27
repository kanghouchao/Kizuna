package com.kizuna.order.application;

import com.kizuna.order.domain.OrderFeeLineKind;

/** 明細維持（lineId）・手入力・設定選択（serviceId / revisionId）のいずれか一つ。組合せは採用時に検証する。 */
public record OrderFeeLineInput(
    String lineId,
    OrderFeeLineKind kind,
    String name,
    Integer amount,
    Integer durationMinutes,
    Integer remuneration,
    String serviceId,
    String revisionId) {}
