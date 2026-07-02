package com.kizuna.service.mail;

import com.kizuna.service.central.config.SystemConfigService;
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
      JavaMailSender sender = resolveSender();
      if (sender == null) {
        // フォールバック: メール設定がなくてもシステムが動作するようログ出力のみ行う
        log.info("[MAIL-FALLBACK] to={} subject={} body={} ", to, subject, body);
        return;
      }
      SimpleMailMessage msg = new SimpleMailMessage();
      systemConfigService
          .getConfigValue("smtp_from")
          .filter(v -> !v.isBlank())
          .ifPresent(msg::setFrom);
      msg.setTo(to);
      msg.setSubject(subject);
      msg.setText(body);
      sender.send(msg);
    } catch (Exception e) {
      log.error("メール送信に失敗しました to={}: {}", to, e.getMessage());
    }
  }

  /** DB の SMTP 設定があればそこから送信クライアントを構築し、なければ環境変数ベースの設定にフォールバックする。 */
  JavaMailSender resolveSender() {
    String host = systemConfigService.getConfigValue("smtp_host").orElse("");
    if (host.isBlank()) {
      return mailSender;
    }
    // ponytail: 送信の度にクライアントを生成する。送信量が増えたら設定更新時に再生成するキャッシュ方式へ
    JavaMailSenderImpl impl = new JavaMailSenderImpl();
    impl.setHost(host);
    impl.setPort(
        systemConfigService
            .getConfigValue("smtp_port")
            .filter(v -> !v.isBlank())
            .map(v -> Integer.parseInt(v.trim()))
            .orElse(25));
    systemConfigService
        .getConfigValue("smtp_username")
        .filter(v -> !v.isBlank())
        .ifPresent(
            username -> {
              impl.setUsername(username);
              systemConfigService.getConfigValue("smtp_password").ifPresent(impl::setPassword);
              impl.getJavaMailProperties().put("mail.smtp.auth", "true");
            });
    return impl;
  }
}
