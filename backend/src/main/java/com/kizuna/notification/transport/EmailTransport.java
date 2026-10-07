package com.kizuna.notification.transport;

public interface EmailTransport {
  enum Result {
    SENT,
    UNAVAILABLE,
    FAILED,
    UNKNOWN
  }

  boolean available();

  Result deliver(String recipient, String subject, String body);
}
