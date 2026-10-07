package com.kizuna.notificationdelivery.application;

public record DeliveryDispatch(
    String deliveryId, String attemptId, Long serviceUserId, Long storeId, Long executionId) {}
