package com.kizuna.notification.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.tenant.domain.event.TenantCreatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantCreatedListenerTest {

  @Mock private MailService mailService;
  @Mock private AppProperties appProperties;
  @InjectMocks private TenantCreatedListener listener;

  @Test
  @DisplayName("テナント作成イベントで登録案内メールを送信すること")
  void onTenantCreated_sendsRegistrationMail() {
    when(appProperties.getScheme()).thenReturn("https");
    TenantCreatedEvent ev =
        new TenantCreatedEvent(1L, "Store1", "store1.example.com", "owner@example.com", "tok-123");

    listener.onTenantCreated(ev);

    verify(mailService)
        .send(
            eq("owner@example.com"),
            eq("[きずな] テナント登録のご案内"),
            contains("https://store1.example.com/init-admin-use?token=tok-123"));
  }

  @Test
  @DisplayName("メール送信失敗でも例外を伝播しないこと")
  void onTenantCreated_swallowsMailFailure() {
    when(appProperties.getScheme()).thenReturn("https");
    doThrow(new RuntimeException("smtp down")).when(mailService).send(any(), any(), any());
    TenantCreatedEvent ev =
        new TenantCreatedEvent(1L, "Store1", "store1.example.com", "owner@example.com", "tok");

    assertThatCode(() -> listener.onTenantCreated(ev)).doesNotThrowAnyException();
  }
}
