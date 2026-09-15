package com.kizuna.order.domain;

import java.io.Serializable;

public record SpecialServiceHistorySnapshot(
    SpecialServiceSnapshot snapshot, boolean requiresAttention) implements Serializable {}
