package com.kizuna.order.application;

import java.time.LocalDate;
import java.time.LocalTime;

/** 会員ポータルからの予約申請の入力。 */
public record MemberOrderApplicationCommand(
    Long storeId,
    String declaredName,
    LocalDate businessDate,
    LocalTime arrivalScheduledStartTime,
    Integer pax,
    String castId,
    String remarks) {}
