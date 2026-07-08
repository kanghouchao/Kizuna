package com.kizuna.shift.domain;

import java.time.LocalDate;
import java.time.LocalTime;

/** シフトの部分更新コマンド。null のフィールドは「変更しない」を意味する（CastPatch と同じ意味論）。 */
public record ShiftPatch(
    String castId, LocalDate workDate, LocalTime startTime, LocalTime endTime, String status) {}
