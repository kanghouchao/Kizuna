package com.kizuna.notification.application;

import com.kizuna.settings.application.SmtpSettings;
import com.kizuna.settings.application.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

/** システム設定（DB）の SMTP 設定を優先して使用するメール送信サービス。 */
@Log4j2
@Service
@RequiredArgsConstructor
public class MailServiceImpl implements MailService {

  private final SystemConfigService systemConfigService;

  @Autowired(required = false)
  private JavaMailSender mailSender;

  @Override
  public void send(String to, String subject, String body) {
    try {
      SmtpSettings smtp = systemConfigService.smtpSettings();
      JavaMailSender sender = resolveSender(smtp);
      if (sender == null) {
        // フォールバック: メール設定がなくてもシステムが動作するようログ出力のみ行う
        log.info("[MAIL-FALLBACK] to={} subject={} body={} ", to, subject, body);
        return;
      }
      SimpleMailMessage msg = new SimpleMailMessage();
      if (smtp.hasFrom()) {
        msg.setFrom(smtp.from());
      }
      msg.setTo(to);
      msg.setSubject(subject);
      msg.setText(body);
      sender.send(msg);
    } catch (Exception e) {
      log.error("メール送信に失敗しました to={}: {}", to, e.getMessage());
    }
  }

  /** DB の SMTP 設定があればそこから送信クライアントを構築し、なければ環境変数ベースの設定にフォールバックする。 */
  JavaMailSender resolveSender(SmtpSettings smtp) {
    if (!smtp.configured()) {
      return mailSender;
    }
    // ponytail: 設定は smtpSettings() 側でキャッシュ済み。送信クライアントの器の生成は軽量なので送信毎で足りる
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
