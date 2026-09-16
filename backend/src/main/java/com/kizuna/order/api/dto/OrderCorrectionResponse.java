package com.kizuna.order.api.dto;

import com.kizuna.order.domain.OrderCourse;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
    List<OrderFeeLineResponse> feeLines,
    List<OrderSpecialServiceResponse> previousSpecialServices,
    List<OrderSpecialServiceResponse> specialServices,
    String orderId,
    Long storeId,
    LocalDate businessDate,
    OffsetDateTime completedAt,
    OffsetDateTime correctedAt,
    Long correctedBy,
    String reason,
    long beforeVersion,
    long afterVersion,
    int previousAccruedRemuneration,
    int accruedRemuneration) {}
