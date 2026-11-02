package com.kizuna.survey.api.dto;

import com.kizuna.receipt.once.Receipt;
import com.kizuna.survey.domain.SurveyValues.Operation;

public record SurveyOperationResponse(
    String id, Operation type, String resourceId, Long committedVersion, boolean replayed) {
  public static SurveyOperationResponse of(Receipt receipt) {
    return new SurveyOperationResponse(
        receipt.id(),
        Operation.ofReceipt(receipt.operation()),
        receipt.targetId(),
        receipt.longFact("committed_version"),
        receipt.replayed());
  }
}
