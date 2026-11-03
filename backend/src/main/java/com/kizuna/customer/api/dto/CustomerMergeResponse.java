package com.kizuna.customer.api.dto;

import com.kizuna.customer.domain.CustomerMerge;

/**
 * 実行された顧客統合の応答。JSON キーは Jackson 設定により snake_case（surviving_customer_id）。
 *
 * <p>件数は統合が実際に移した数で、統合履歴に残る値と同一である。
 */
public record CustomerMergeResponse(
    String survivingCustomerId,
    int movedOrderCount,
    int movedLinkCount,
    String mergeId,
    int movedContactCount) {
  public static CustomerMergeResponse of(CustomerMerge merge) {
    return new CustomerMergeResponse(
        merge.getSurvivingCustomerId(),
        merge.getMovedOrderCount(),
        merge.getMovedLinkCount(),
        merge.getId(),
        merge.getMovedContactCount());
  }
}
