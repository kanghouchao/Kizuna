package com.kizuna.order.application;

import java.util.List;

public record SelfRemunerationDetail(
    SelfRemunerationOrder order, long version, List<SelfRemunerationLine> items) {}
