package com.kizuna.notification.transport;

/** チャネルごとの送信手段。チャネルごとに一つの実装を置き、送信正本は具体実装を知らない。 */
public interface ContactTransport {
  enum Channel {
    EMAIL
  }

  enum Result {
    SENT,
    UNAVAILABLE,
    FAILED,
    UNKNOWN
  }

  Channel type();

  boolean available();

  Result deliver(String recipient, String subject, String body);
}
