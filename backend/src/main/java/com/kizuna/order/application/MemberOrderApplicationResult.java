package com.kizuna.order.application;

import com.kizuna.order.domain.OrderApplicationStatus;
import java.time.LocalDate;
import java.time.LocalTime;

/** 会員本人に返す予約申請。{@code expired} は希望日を過ぎても未処理の申請を示す導出値で、行の状態は動かない。 */
public record MemberOrderApplicationResult(
    String id,
    Long storeId,
    String storeName,
    LocalDate businessDate,
    LocalTime arrivalScheduledStartTime,
    Integer pax,
    String castName,
    OrderApplicationStatus status,
    boolean expired) {}
