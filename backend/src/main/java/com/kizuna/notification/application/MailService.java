package com.kizuna.notification.application;

import com.kizuna.notification.transport.EmailTransport;
import com.kizuna.settings.application.SmtpSettings;
import com.kizuna.settings.application.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

/** システム設定（DB）の SMTP 設定を優先して使用するメール送信サービス。 */
@Log4j2
@Service
@RequiredArgsConstructor
public class MailService implements EmailTransport {

  private final SystemConfigService systemConfigService;
  // required=false 相当: JavaMailSender Bean（spring.mail.host 未設定時は不在）が無くても起動できるようにする
  private final ObjectProvider<JavaMailSender> mailSenderProvider;

  public void send(String to, String subject, String body) {
    var result = deliver(to, subject, body);
    if (result != Result.SENT) log.warn("メール送信が完了しませんでした result={}", result);
  }

  @Override
  public boolean available() {
    try {
      return resolveSender(systemConfigService.smtpSettings()) != null;
    } catch (RuntimeException failure) {
      return false;
    }
  }

  @Override
  public Result deliver(String to, String subject, String body) {
    SmtpSettings smtp;
    JavaMailSender sender;
    try {
      smtp = systemConfigService.smtpSettings();
      sender = resolveSender(smtp);
    } catch (RuntimeException failure) {
      return Result.UNAVAILABLE;
    }
    if (sender == null) return Result.UNAVAILABLE;
    try {
      SimpleMailMessage msg = new SimpleMailMessage();
      if (smtp.hasFrom()) msg.setFrom(smtp.from());
      msg.setTo(to);
      msg.setSubject(subject);
      msg.setText(body);
      sender.send(msg);
      return Result.SENT;
    } catch (MailAuthenticationException | MailParseException rejected) {
      return Result.FAILED;
    } catch (RuntimeException uncertain) {
      // 提供方の受理後にも通信が切れるため、例外だけでは未送信と断定しない。
      return Result.UNKNOWN;
    }
  }

  /** DB の SMTP 設定があればそこから送信クライアントを構築し、なければ環境変数ベースの設定にフォールバックする。 */
  JavaMailSender resolveSender(SmtpSettings smtp) {
    if (!smtp.configured()) {
      return mailSenderProvider.getIfAvailable();
    }
    // 送信クライアントの器の生成は軽量なので送信毎の組み立てで足りる（送信は低頻度）
    JavaMailSenderImpl impl = new JavaMailSenderImpl();
    impl.setHost(smtp.host());
    impl.setPort(smtp.port());
    if (smtp.hasAuth()) {
      impl.setUsername(smtp.username());
      impl.setPassword(smtp.password());
      impl.getJavaMailProperties().put("mail.smtp.auth", "true");
    }
    return impl;
  }
}
