package com.kizuna.notificationdelivery.domain;

public enum DeliveryStatus {
  DRAFT,
  QUEUED,
  DISPATCHED,
  SENDING,
  SENT,
  FAILED,
  BLOCKED,
  UNKNOWN
}
