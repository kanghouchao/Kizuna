package com.kizuna.order.application;

import java.util.List;

public record SelfRemunerationState(
    List<SelfRemunerationLine> items,
    int agreedRemuneration,
    int accruedRemuneration,
    boolean completionInvalidated) {}
