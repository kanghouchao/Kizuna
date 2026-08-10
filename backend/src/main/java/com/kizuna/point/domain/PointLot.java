package com.kizuna.point.domain;

import java.time.LocalDate;

/**
 * 残高計算の入力となる加算ロット 1 件。台帳の加算仕訳と、そこへ引き当てられた消費量の合計を組にしたもの。
 *
 * @param entryId 加算仕訳の ID（引き当て先として記録される）
 * @param amount 加算量（常に正）
 * @param expiresOn 有効期限。期限なしは null
 * @param consumed このロットへ既に引き当てられた合計
 */
public record PointLot(Long entryId, int amount, LocalDate expiresOn, int consumed) {}
