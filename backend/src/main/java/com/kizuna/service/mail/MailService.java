package com.kizuna.service.mail;

/** Simple mail sending abstraction used by application listeners. */
public interface MailService {
  void send(String to, String subject, String body);
}
