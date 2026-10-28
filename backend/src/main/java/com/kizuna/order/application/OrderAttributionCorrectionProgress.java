package com.kizuna.order.application;

/** 訂正の進み具合。付与の合計と、既に差し引いた合計。 */
public record OrderAttributionCorrectionProgress(long grantedPoints, long correctedPoints) {}
