package com.kizuna.order.api.dto;

import java.util.List;

public record OrderPreviewResponse(
    String confirmationToken,
    CourseCondition course,
    List<OrderFeeLineResponse> feeLines,
    int totalFee,
    Points points) {
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
      Long pointBalance,
      String memberCode,
      int usageUnit,
      int usePoints,
      int grantPoints) {}
}
