package com.kizuna.order.domain;

import java.time.LocalDate;
import java.time.LocalTime;

/** 店舗が起こす受注の出生時の内容。 */
public record NewStoreOrder(
    String replacementForOrderId,
    LocalDate businessDate,
    LocalTime arrivalScheduledStartTime,
    LocalTime arrivalScheduledEndTime,
    String locationAddress,
    String locationBuilding,
    Integer pax,
    ReceptionRoute receptionRoute,
    String carrier,
    String mediaName,
    String remarks,
    String castDriverMessage) {}
