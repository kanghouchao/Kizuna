package com.cms.service.mail;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@SuppressWarnings("null")
public class MailServiceImplTests {

  @Test
  void send_withMailSender_callsJavaMailSender() {
    JavaMailSender mailSender = mock(JavaMailSender.class);
    MailServiceImpl svc = new MailServiceImpl();
    try {
      java.lang.reflect.Field f = MailServiceImpl.class.getDeclaredField("mailSender");
      f.setAccessible(true);
      f.set(svc, mailSender);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    svc.send("to@example.com", "subj", "body");

    verify(mailSender).send(any(SimpleMailMessage.class));
  }

  @Test
  void send_withMailSender_ignoredOnException() {
    JavaMailSender mailSender = mock(JavaMailSender.class);
    doThrow(new RuntimeException("boom")).when(mailSender).send(any(SimpleMailMessage.class));
    MailServiceImpl svc = new MailServiceImpl();
    try {
      java.lang.reflect.Field f = MailServiceImpl.class.getDeclaredField("mailSender");
      f.setAccessible(true);
      f.set(svc, mailSender);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    svc.send("to@example.com", "subj", "body");
  }

  @Test
  void send_whenMailSenderNull_logsFallbackAndNotThrow() {
    MailServiceImpl svc = new MailServiceImpl();
    svc.send("to@example.com", "subj", "body");
  }
}
