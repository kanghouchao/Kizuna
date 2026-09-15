package com.kizuna.order.api.dto;

import com.kizuna.order.domain.OrderCourse;

public record OrderCorrectionResponse(
    String correctionId,
    Integer previousTotalFee,
    Integer totalFee,
    OrderCourse previousCourse,
    OrderCourse course) {}
