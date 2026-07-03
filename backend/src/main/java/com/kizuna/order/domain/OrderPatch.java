package com.kizuna.order.domain;

import java.time.LocalTime;
import java.util.List;

/** 注文の部分更新コマンド。null のフィールドは「変更しない」を意味する（従来の IGNORE 戦略と同じ意味論）。 */
public record OrderPatch(
    String storeName,
    LocalTime arrivalScheduledStartTime,
    LocalTime arrivalScheduledEndTime,
    Integer courseMinutes,
    Integer extensionMinutes,
    List<String> optionCodes,
    String discountName,
    Integer manualDiscount,
    Integer usedPoints,
    Integer manualGrantPoints,
    String remarks,
    String castDriverMessage) {}
