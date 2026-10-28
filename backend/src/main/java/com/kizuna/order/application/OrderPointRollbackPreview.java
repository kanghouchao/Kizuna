package com.kizuna.order.application;

import com.kizuna.point.application.PointLedgerService.PointRollbackHistory;

/** 受注に限った処置見込みと、高々一件の確定履歴。会員の残高は含めない。 */
public record OrderPointRollbackPreview(
    boolean alreadyRolledBack,
    String memberCode,
    long cancellablePoints,
    long reversibleUsedPoints,
    int currentTotalFee,
    int offsetAmount,
    int resultingTotalFee,
    PointRollbackHistory rollback) {}
