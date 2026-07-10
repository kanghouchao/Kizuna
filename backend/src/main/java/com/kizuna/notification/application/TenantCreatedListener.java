package com.kizuna.notification.application;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.tenant.domain.event.TenantCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class TenantCreatedListener {

  private final MailService mailService;
  private final AppProperties appProperties;

  @ApplicationModuleListener
  public void onTenantCreated(TenantCreatedEvent ev) {
    String subject = "[きずな] テナント登録のご案内";
    String link =
        String.format(
            "%s://%s/register?token=%s", appProperties.getScheme(), ev.domain(), ev.token());
    String body =
        String.format(
            "きずなへようこそ, %s，\n\n登録を完了するには次のリンクをクリックしてください： %s \n\nこのリンクは7日間有効です。", ev.name(), link);

    try {
      mailService.send(ev.email(), subject, body);
    } catch (Exception e) {
      log.error("failed to send tenant registration email to {}: {}", ev.email(), e.getMessage());
    }
  }
}
