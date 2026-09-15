package com.kizuna.order.api.dto;

import com.kizuna.order.domain.OrderCourse;
import java.util.List;

public record OrderCorrectionResponse(
    String correctionId,
    Integer previousTotalFee,
    Integer totalFee,
    OrderCourse previousCourse,
    OrderCourse course,
    int previousTotalRemuneration,
    int totalRemuneration,
    int previousTotalDurationMinutes,
    int totalDurationMinutes,
    List<OrderFeeLineResponse> previousFeeLines,
    List<OrderFeeLineResponse> feeLines) {}
