package com.kizuna.order.application;

import java.time.LocalDate;

/** 会員本人に返す来店 1 件。{@code grantedPoints} は台帳に付与行が無ければ 0。 */
public record MemberVisitSummary(
    LocalDate visitedOn, String storeName, Integer pax, String castName, long grantedPoints) {}
