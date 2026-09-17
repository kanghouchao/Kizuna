package com.kizuna.order.api.dto;

import java.util.List;

public record SelfRemunerationSnapshot(
    List<SelfRemunerationItem> items,
    int agreedRemuneration,
    int accruedRemuneration,
    boolean completionInvalidated) {}
