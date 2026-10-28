package com.kizuna.order.application;

/** 誤付与の差し引き。冪等キーが同じ再送は初回の記帳に収束する。 */
public record OrderAttributionCorrectionCommand(
    Long attributionId, Integer points, String reason, String idempotencyKey) {}
