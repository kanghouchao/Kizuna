package com.kizuna.customer.api.dto;

import com.kizuna.customer.domain.CustomerMerge;
import com.kizuna.customer.domain.MergeSnapshot;
import java.time.OffsetDateTime;
import java.util.List;

public record CustomerMergeAuditResponse(
    String id,
    String survivingCustomerId,
    String mergedCustomerId,
    MergeSnapshot beforeSurviving,
    MergeSnapshot beforeMerged,
    MergeSnapshot afterSurviving,
    List<String> movedOrderIds,
    List<String> movedContactIds,
    List<String> movedLinkIds,
    String operationReason,
    Long mergedBy,
    String mergedByName,
    OffsetDateTime mergedAt) {
  public static CustomerMergeAuditResponse from(CustomerMerge merge) {
    var e = merge.getEvidence();
    return new CustomerMergeAuditResponse(
        merge.getId(),
        merge.getSurvivingCustomerId(),
        merge.getMergedCustomerId(),
        e.beforeSurviving(),
        e.beforeMerged(),
        e.afterSurviving(),
        e.movedOrderIds(),
        e.movedContactIds(),
        e.movedLinkIds(),
        merge.getOperationReason(),
        e.actorId(),
        e.actorName(),
        merge.getMergedAt());
  }
}
