package com.kizuna.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.settings.application.SmtpSettings;
import com.kizuna.settings.application.SystemConfigService;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@ExtendWith(MockitoExtension.class)
class MailServiceImplTests {

  private static final SmtpSettings NOT_CONFIGURED = new SmtpSettings("", 25, "", "", "");

  @Mock private SystemConfigService systemConfigService;

  private MailServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new MailServiceImpl(systemConfigService);
  }

  /** 環境変数ベースのフォールバック送信クライアントをリフレクションで注入する */
  private void injectMailSender(JavaMailSender sender) {
    try {
      Field field = MailServiceImpl.class.getDeclaredField("mailSender");
      field.setAccessible(true);
      field.set(service, sender);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  @DisplayName("SMTP 設定もフォールバックもない場合はログのみで例外を投げないこと")
  void send_noConfigNoFallback() {
    when(systemConfigService.smtpSettings()).thenReturn(NOT_CONFIGURED);

    service.send("to@example.com", "件名", "本文");
    // 例外が出なければ成功
  }

  @Test
  @DisplayName("DB の SMTP 設定がない場合はフォールバック送信クライアントを使用すること")
  void send_usesFallbackSender() {
    when(systemConfigService.smtpSettings()).thenReturn(NOT_CONFIGURED);
    JavaMailSender fallback = mock(JavaMailSender.class);
    injectMailSender(fallback);

    service.send("to@example.com", "件名", "本文");

    verify(fallback).send(any(SimpleMailMessage.class));
  }

  @Test
  @DisplayName("smtp_from が設定されていれば送信元が設定されること")
  void send_setsFromAddress() {
    when(systemConfigService.smtpSettings())
        .thenReturn(new SmtpSettings("", 25, "", "", "noreply@kizuna.test"));
    JavaMailSender fallback = mock(JavaMailSender.class);
    injectMailSender(fallback);

    service.send("to@example.com", "件名", "本文");

    ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(fallback).send(captor.capture());
    assertThat(captor.getValue().getFrom()).isEqualTo("noreply@kizuna.test");
  }

  @Test
  @DisplayName("DB の SMTP 設定から送信クライアントが構築されること")
  void resolveSender_buildsFromDbConfig() {
    SmtpSettings smtp =
        new SmtpSettings(
            "smtp.example.com", 587, "user", "test-placeholder-secret", "noreply@kizuna.test");

    JavaMailSender sender = service.resolveSender(smtp);

    assertThat(sender).isInstanceOf(JavaMailSenderImpl.class);
    JavaMailSenderImpl impl = (JavaMailSenderImpl) sender;
    assertThat(impl.getHost()).isEqualTo("smtp.example.com");
    assertThat(impl.getPort()).isEqualTo(587);
    assertThat(impl.getUsername()).isEqualTo("user");
    assertThat(impl.getPassword()).isEqualTo("test-placeholder-secret");
    assertThat(impl.getJavaMailProperties().getProperty("mail.smtp.auth")).isEqualTo("true");
  }

  @Test
  @DisplayName("smtp_username が空なら認証なしで構築されること")
  void resolveSender_withoutAuth() {
    SmtpSettings smtp = new SmtpSettings("smtp.example.com", 25, "", "", "");

    JavaMailSenderImpl impl = (JavaMailSenderImpl) service.resolveSender(smtp);

    assertThat(impl.getPort()).isEqualTo(25);
    assertThat(impl.getUsername()).isNull();
    assertThat(impl.getJavaMailProperties().getProperty("mail.smtp.auth")).isNull();
  }

  @Test
  @DisplayName("送信時の例外は握りつぶしてログに記録すること")
  void send_swallowsException() {
    when(systemConfigService.smtpSettings()).thenReturn(NOT_CONFIGURED);
    JavaMailSender fallback = mock(JavaMailSender.class);
    doThrow(new RuntimeException("接続失敗")).when(fallback).send(any(SimpleMailMessage.class));
    injectMailSender(fallback);

    service.send("to@example.com", "件名", "本文");
    // 例外が伝播しなければ成功
  }
}
