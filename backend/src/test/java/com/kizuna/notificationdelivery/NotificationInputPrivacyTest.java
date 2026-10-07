package com.kizuna.notificationdelivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.notificationdelivery.api.store.NotificationDeliveryController;
import com.kizuna.notificationdelivery.application.NotificationService;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.exception.CommonExceptionHandler;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.store.application.StoreActivationService;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificationDeliveryController.class)
@Import({NotificationInputPrivacyTest.MethodSecurity.class, StoreContext.class})
class NotificationInputPrivacyTest {
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurity {}

  @Autowired MockMvc mvc;
  @MockitoBean NotificationService notifications;
  @MockitoBean SystemConfigService configs;
  @MockitoBean StoreExistenceCheck stores;
  @MockitoBean StoreActivationService activation;

  @Test
  void rejectedNotificationPayloadDoesNotEnterLogsOrResponses() throws Exception {
    when(stores.exists(anyLong())).thenReturn(true);
    var auth =
        new UsernamePasswordAuthenticationToken(
            "actor",
            "unused",
            List.of(
                new SimpleGrantedAuthority("PERM_NOTIFICATION_VIEW"),
                new SimpleGrantedAuthority("PERM_NOTIFICATION_MANAGE")));
    TestSecurityContextHolder.setAuthentication(auth);
    String secret = "notification-private@example.invalid 非公開本文";
    String valid =
        """
        {"source_type":"ORDER","source_id":"1","channel":"EMAIL","purpose":"BUSINESS",
         "subject":"非公開件名","body":"%s","scheduled_at":"2026-10-07T00:00:00Z",
         "dedupe_key":"privacy-test"}
        """
            .formatted(secret);
    var capture = new CaptureAppender();
    Logger logger = (Logger) LogManager.getLogger(CommonExceptionHandler.class);
    capture.start();
    logger.addAppender(capture);
    try {
      for (String payload :
          List.of(
              valid.replace("\"ORDER\"", "\"" + secret + "\""),
              valid.replace("\"source_id\":\"1\"", "\"source_id\":\"" + secret + "\""),
              valid.substring(0, valid.lastIndexOf('}')))) {
        var response =
            mvc.perform(
                    post("/store/notification-deliveries")
                        .with(csrf())
                        .header("X-Role", "store")
                        .header("X-Store-ID", "1")
                        .with(
                            request -> {
                              request.setUserPrincipal(auth);
                              return request;
                            })
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse();
        assertThat(response.getContentAsString()).doesNotContain(secret, "非公開件名");
      }
      var actionResponse =
          mvc.perform(
                  post("/store/notification-deliveries/1/queue")
                      .with(csrf())
                      .header("X-Role", "store")
                      .header("X-Store-ID", "1")
                      .with(
                          request -> {
                            request.setUserPrincipal(auth);
                            return request;
                          })
                      .contentType("application/json")
                      .content("{\"reason\":\"" + secret + "\"}"))
              .andExpect(status().isBadRequest())
              .andReturn()
              .getResponse();
      assertThat(actionResponse.getContentAsString()).doesNotContain(secret);
      assertThat(capture.events)
          .extracting(event -> event.getMessage().getFormattedMessage())
          .containsExactly("リクエスト本文解析エラー", "入力の文字数と内容を確認してください", "リクエスト本文解析エラー", "入力検証エラー");
      assertThat(capture.events).allSatisfy(event -> assertThat(event.getThrown()).isNull());
      verifyNoInteractions(notifications);
    } finally {
      logger.removeAppender(capture);
      capture.stop();
      TestSecurityContextHolder.clearContext();
    }
  }

  private static class CaptureAppender extends AbstractAppender {
    private final List<LogEvent> events = new ArrayList<>();

    CaptureAppender() {
      super("notification-input-privacy", null, null, true, Property.EMPTY_ARRAY);
    }

    @Override
    public void append(LogEvent event) {
      events.add(event.toImmutable());
    }
  }
}
