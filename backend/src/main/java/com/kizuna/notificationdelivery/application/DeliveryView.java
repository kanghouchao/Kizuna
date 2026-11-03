package com.kizuna.notificationdelivery.application;

import com.kizuna.notificationdelivery.domain.NotificationDelivery;
import com.kizuna.order.contact.BusinessContactDecision;

/** 通知 1 件と、読んだ時点の連絡可否の判定・送信手段の有無。 */
public record DeliveryView(
    NotificationDelivery row,
    BusinessContactDecision contactDecision,
    boolean transportAvailable) {}
