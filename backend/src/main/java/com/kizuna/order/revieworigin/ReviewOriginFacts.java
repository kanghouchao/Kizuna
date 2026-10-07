package com.kizuna.order.revieworigin;

public record ReviewOriginFacts(
    String orderId, Long orderVersion, String status, boolean completionInvalidated) {}
