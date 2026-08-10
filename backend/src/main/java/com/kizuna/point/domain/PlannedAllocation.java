package com.kizuna.point.domain;

/**
 * 消費計画の 1 行。どの加算ロットから何ポイント引き当てるかを表し、そのまま {@link PointAllocation} へ写される。
 *
 * @param sourceEntryId 引き当て元の加算仕訳 ID
 * @param amount 引き当て量（常に正）
 */
public record PlannedAllocation(Long sourceEntryId, int amount) {}
