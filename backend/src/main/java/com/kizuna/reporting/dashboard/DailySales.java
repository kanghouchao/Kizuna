package com.kizuna.reporting.dashboard;

import java.time.LocalDate;

public record DailySales(LocalDate businessDate, SalesTotals totals) {}
