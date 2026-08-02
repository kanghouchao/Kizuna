package com.kizuna.notification.application;

import com.kizuna.settings.application.SmtpSettings;
import com.kizuna.settings.application.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

/** システム設定（DB）の SMTP 設定を優先して使用するメール送信サービス。 */
@Log4j2
@Service
@RequiredArgsConstructor
public class MailService {

  private final SystemConfigService systemConfigService;
  // required=false 相当: JavaMailSender Bean（spring.mail.host 未設定時は不在）が無くても起動できるようにする
  private final ObjectProvider<JavaMailSender> mailSenderProvider;

  public void send(String to, String subject, String body) {
    // 送信は呼び出し元の業務を止めない（例外を外へ出さない）。ただし「設定が読めない」と「送信に失敗した」は
    // 別の故障であり、握り潰すと SMTP を設定してもメールが出ない静かな故障になるため、ログで区別する。
    SmtpSettings smtp;
    JavaMailSender sender;
    try {
      smtp = systemConfigService.smtpSettings();
      sender = resolveSender(smtp);
    } catch (Exception e) {
      log.error("SMTP 設定の読み取りに失敗したためメールを送信できません to={}", to, e);
      return;
    }
    if (sender == null) {
      // フォールバック: メール設定がなくてもシステムが動作するようログ出力のみ行う
      log.info("[MAIL-FALLBACK] to={} subject={} body={} ", to, subject, body);
      return;
    }
    try {
      SimpleMailMessage msg = new SimpleMailMessage();
      if (smtp.hasFrom()) {
        msg.setFrom(smtp.from());
      }
      msg.setTo(to);
      msg.setSubject(subject);
      msg.setText(body);
      sender.send(msg);
    } catch (Exception e) {
      log.error("メール送信に失敗しました to={}", to, e);
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
