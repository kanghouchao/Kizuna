package com.kizuna.notification.transport;

import com.kizuna.customer.domain.ContactType;

/** 連絡先種別ごとの送信手段。種別ごとに一つの実装を置き、送信正本は具体実装を知らない。 */
public interface ContactTransport {
  enum Result {
    SENT,
    UNAVAILABLE,
    FAILED,
    UNKNOWN
  }

  ContactType type();

  boolean available();

  Result deliver(String recipient, String subject, String body);
}
