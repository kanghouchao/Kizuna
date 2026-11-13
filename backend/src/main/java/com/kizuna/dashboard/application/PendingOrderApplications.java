package com.kizuna.dashboard.application;

import java.time.LocalDate;

public record PendingOrderApplications(LocalDate businessDate, long count) {}
