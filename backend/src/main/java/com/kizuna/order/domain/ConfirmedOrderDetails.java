package com.kizuna.order.domain;

import java.time.LocalDate;
import java.time.LocalTime;

/** 予約申請の確定で店舗が定めた、受注の出生時の内容。 */
public record ConfirmedOrderDetails(
    LocalDate businessDate,
    LocalTime arrivalScheduledStartTime,
    LocalTime arrivalScheduledEndTime,
    Integer pax,
    String remarks) {}
