package com.kizuna.remuneration.application;

import java.time.LocalDate;

public record AcceptanceCommand(Long storeId, LocalDate businessDate, String contentDigest) {}
