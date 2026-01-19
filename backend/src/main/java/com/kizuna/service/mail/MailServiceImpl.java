package com.kizuna.service.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class MailServiceImpl implements MailService {

  @Autowired(required = false)
  private JavaMailSender mailSender;

  @Override
  public void send(String to, String subject, String body) {
    if (mailSender != null) {
      try {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(body);
        mailSender.send(msg);
      } catch (Exception e) {
        log.error("failed to send mail to {}: {}", to, e.getMessage());
      }
    } else {
      // Fallback: just log the message so system can run without mail config
      log.info("[MAIL-FALLBACK] to={} subject={} body={} ", to, subject, body);
    }
  }
}
