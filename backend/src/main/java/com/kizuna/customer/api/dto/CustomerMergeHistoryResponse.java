package com.kizuna.customer.api.dto;

import java.time.OffsetDateTime;

/**
 * 統合履歴 1 件の応答。JSON キーは Jackson 設定により snake_case（merged_at）。
 *
 * <p>相手の行は id と表示名の両方を持つ。統合に取消は無く、誤統合の修復は「どの行をどの行へ」を根拠とする人手作業なので（ADR 0010）、 id
 * だけでは読み手が相手を思い出せない。実行者名は欠けうる（利用者の削除）が、その場合も行は返る。
 */
public record CustomerMergeHistoryResponse(
    String id,
    MergeDirection direction,
    String counterpartCustomerId,
    String counterpartCustomerName,
    String mergedByName,
    OffsetDateTime mergedAt,
    int movedOrderCount,
    int movedLinkCount) {}
