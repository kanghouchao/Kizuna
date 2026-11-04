package com.kizuna.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.notification.transport.ContactTransport;
import com.kizuna.settings.application.SmtpSettings;
import com.kizuna.settings.application.SystemConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mock.env.MockEnvironment;

@ExtendWith(MockitoExtension.class)
class MailServiceTests {

  private static final SmtpSettings NOT_CONFIGURED = new SmtpSettings("", 25, "", "", "");

  @Mock private SystemConfigService systemConfigService;
  @Mock private JavaMailSender mailSender;
  @Mock private ObjectProvider<JavaMailSender> mailSenderProvider;

  private MailService service;

  @BeforeEach
  void setUp() {
    service =
        new MailService(
            systemConfigService,
            mailSenderProvider,
            new MockEnvironment()
                .withProperty("spring.mail.properties.mail.smtp.timeout", "10000")
                .withProperty("spring.mail.properties.mail.smtp.connectiontimeout", "5000")
                .withProperty("spring.mail.properties.mail.smtp.writetimeout", "10000")
                .withProperty("spring.mail.properties.mail.smtp.starttls.enable", "true"));
  }

  @Test
  void unavailableTransportNeverReportsSent() {
    when(systemConfigService.smtpSettings()).thenReturn(NOT_CONFIGURED);

    assertThat(service.deliver("recipient@example.invalid", "件名", "本文"))
        .isEqualTo(ContactTransport.Result.UNAVAILABLE);
    verifyNoInteractions(mailSender);
  }

  @Test
  void providerAcknowledgementIsDistinctFromUnknownAndDefiniteFailure() {
    when(systemConfigService.smtpSettings()).thenReturn(NOT_CONFIGURED);
    when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
    assertThat(service.available()).isTrue();
    assertThat(service.deliver("recipient@example.invalid", "件名", "本文"))
        .isEqualTo(ContactTransport.Result.SENT);
    doThrow(new MailSendException("機密な提供方応答")).when(mailSender).send(any(SimpleMailMessage.class));
    assertThat(service.deliver("recipient@example.invalid", "件名", "本文"))
        .isEqualTo(ContactTransport.Result.UNKNOWN);
    doThrow(new MailAuthenticationException("認証拒否"))
        .when(mailSender)
        .send(any(SimpleMailMessage.class));
    assertThat(service.deliver("recipient@example.invalid", "件名", "本文"))
        .isEqualTo(ContactTransport.Result.FAILED);
  }

  @Test
  void configurationFailureIsUnavailable() {
    when(systemConfigService.smtpSettings()).thenThrow(new IllegalStateException("機密情報"));
    assertThat(service.available()).isFalse();
    assertThat(service.deliver("recipient@example.invalid", "件名", "本文"))
        .isEqualTo(ContactTransport.Result.UNAVAILABLE);
    verifyNoInteractions(mailSender);
  }

  @Test
  @DisplayName("SMTP が未設定でも既存呼び出し元へ例外を伝播しないこと")
  void send_noConfigNoFallback() {
    when(systemConfigService.smtpSettings()).thenReturn(NOT_CONFIGURED);

    service.send("to@example.com", "件名", "本文");
  }

  @Test
  @DisplayName("SMTP 設定の読み取りが例外でも send は例外を外へ出さず、送信クライアントにも触れないこと")
  void send_settingsReadFailureIsLoggedNotThrown() {
    when(systemConfigService.smtpSettings())
        .thenThrow(new IllegalStateException("Cannot serialize value"));

    service.send("to@example.com", "件名", "本文");

    verifyNoInteractions(mailSenderProvider, mailSender);
  }

  @Test
  @DisplayName("DB の SMTP 設定がない場合はフォールバック送信クライアントを使用すること")
  void send_usesFallbackSender() {
    when(systemConfigService.smtpSettings()).thenReturn(NOT_CONFIGURED);
    when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);

    service.send("to@example.com", "件名", "本文");

    verify(mailSender).send(any(SimpleMailMessage.class));
  }

  @Test
  @DisplayName("smtp_from が設定されていれば送信元が設定されること")
  void send_setsFromAddress() {
    when(systemConfigService.smtpSettings())
        .thenReturn(new SmtpSettings("", 25, "", "", "noreply@kizuna.test"));
    when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);

    service.send("to@example.com", "件名", "本文");

    ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(captor.capture());
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
  @DisplayName("DB の SMTP 設定で組む送信クライアントにも、設定ファイルの待ちの上限が載ること")
  void resolveSender_appliesConfiguredTimeouts() {
    JavaMailSenderImpl impl =
        (JavaMailSenderImpl)
            service.resolveSender(new SmtpSettings("smtp.example.com", 25, "", "", ""));

    assertThat(impl.getJavaMailProperties())
        .containsEntry("mail.smtp.timeout", "10000")
        .containsEntry("mail.smtp.connectiontimeout", "5000")
        .containsEntry("mail.smtp.writetimeout", "10000")
        .doesNotContainKey("mail.smtp.starttls.enable");
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
    when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
    doThrow(new RuntimeException("接続失敗")).when(mailSender).send(any(SimpleMailMessage.class));

    service.send("to@example.com", "件名", "本文");
  }
}
