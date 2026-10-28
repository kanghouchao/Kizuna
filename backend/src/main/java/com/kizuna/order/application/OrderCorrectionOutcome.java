package com.kizuna.order.application;

import com.kizuna.order.domain.OrderCorrection;
import com.kizuna.order.domain.OrderCorrectionSnapshot;
import java.util.List;

/** 訂正の結果。前後の提供事実は訂正の前後で取った不変な写しで、集約の後続の変更に影響されない。 */
public record OrderCorrectionOutcome(
    OrderCorrection correction,
    OrderCorrectionSnapshot before,
    OrderCorrectionSnapshot after,
    List<SpecialServiceStatus> specialServices) {}
