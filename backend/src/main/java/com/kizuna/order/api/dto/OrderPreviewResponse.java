package com.kizuna.order.api.dto;

import com.kizuna.customer.contact.GuestContactImport;
import java.util.List;

public record OrderPreviewResponse(
    String confirmationToken,
    CourseCondition course,
    List<OrderFeeLineResponse> feeLines,
    int totalFee,
    int pointBasisAmount,
    int totalDurationMinutes,
    int totalRemuneration,
    Points points,
    List<OrderSpecialServiceResponse> specialServices,
    boolean requiresAttention,
    int unresolvedSpecialServiceCount,
    List<GuestContactImport> contactImports) {
  public record CourseCondition(
      String serviceId,
      String revisionId,
      long revisionNumber,
      String name,
      int durationMinutes,
      int price,
      int remuneration,
      String adoptionBasis) {}

  public record Points(
      boolean memberLinked,
      boolean redemptionEligible,
      Long pointBalance,
      String memberCode,
      int usageUnit,
      int usePoints,
      int grantPoints) {}
}
