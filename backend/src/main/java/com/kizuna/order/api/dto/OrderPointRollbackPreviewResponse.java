package com.kizuna.order.api.dto;

/** 受注に限った処置見込みと、高々一件の確定履歴。会員の残高は公開しない。 */
public record OrderPointRollbackPreviewResponse(
    boolean alreadyRolledBack,
    String memberCode,
    long cancellablePoints,
    long reversibleUsedPoints,
    int currentTotalFee,
    int offsetAmount,
    int resultingTotalFee,
    OrderPointRollbackResponse rollback) {}
