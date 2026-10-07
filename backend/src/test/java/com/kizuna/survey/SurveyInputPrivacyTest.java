package com.kizuna.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.exception.CommonExceptionHandler;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.store.application.StoreActivationService;
import com.kizuna.survey.api.store.SurveyController;
import com.kizuna.survey.application.SurveyReadService;
import com.kizuna.survey.application.SurveyService;
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

@WebMvcTest(SurveyController.class)
@Import({SurveyInputPrivacyTest.MethodSecurity.class, StoreContext.class})
class SurveyInputPrivacyTest {
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurity {}

  @Autowired MockMvc mvc;
  @MockitoBean SurveyService reviews;
  @MockitoBean SurveyReadService reads;
  @MockitoBean SystemConfigService configs;
  @MockitoBean StoreExistenceCheck stores;
  @MockitoBean StoreActivationService activation;

  @Test
  void rejectedPrivateReviewInputNeverEntersLogsOrResponses() throws Exception {
    when(stores.exists(anyLong())).thenReturn(true);
    var auth =
        new UsernamePasswordAuthenticationToken(
            "actor",
            "unused",
            List.of(
                new SimpleGrantedAuthority("PERM_SURVEY_VIEW"),
                new SimpleGrantedAuthority("PERM_SURVEY_MANAGE")));
    TestSecurityContextHolder.setAuthentication(auth);
    String secret = "survey-private@example.invalid 非公開本文";
    String valid =
        """
        {"title":"%s","questions":[{"question_key":"q1","type":"TEXT","prompt":"非公開設問文","required":true,"options":[]}],"dedupe_key":"privacy-test"}
        """
            .formatted(secret);
    var capture = new CaptureAppender();
    Logger logger = (Logger) LogManager.getLogger(CommonExceptionHandler.class);
    capture.start();
    logger.addAppender(capture);
    try {
      for (String payload :
          List.of(
              valid.replace("\"TEXT\"", "\"" + secret + "\""),
              valid.replace("\"required\":true", "\"required\":\"" + secret + "\""),
              valid.replace("\"question_key\":\"q1\"", "\"question_key\":\"" + secret + "\""),
              valid.substring(0, valid.lastIndexOf('}')))) {
        var response =
            mvc.perform(
                    post("/store/surveys")
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
        assertThat(response.getContentAsString()).doesNotContain(secret, "非公開設問文");
      }
      var actionResponse =
          mvc.perform(
                  post("/store/surveys/1/revisions/2/openings")
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
      assertThat(actionResponse.getContentAsString()).doesNotContain(secret, "非公開設問文");
      assertThat(capture.events)
          .extracting(event -> event.getMessage().getFormattedMessage())
          .allSatisfy(message -> assertThat(message).doesNotContain(secret, "非公開設問文"));
      assertThat(capture.events).allSatisfy(event -> assertThat(event.getThrown()).isNull());
      verifyNoInteractions(reviews);
    } finally {
      logger.removeAppender(capture);
      capture.stop();
      TestSecurityContextHolder.clearContext();
    }
  }

  private static class CaptureAppender extends AbstractAppender {
    private final List<LogEvent> events = new ArrayList<>();

    CaptureAppender() {
      super("survey-input-privacy", null, null, true, Property.EMPTY_ARRAY);
    }

    @Override
    public void append(LogEvent event) {
      events.add(event.toImmutable());
    }
  }
}
