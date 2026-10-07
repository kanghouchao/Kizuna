package com.kizuna.recruitment.api.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.recruitment.api.dto.ApplicantSummaryResponse;
import com.kizuna.recruitment.application.ApplicantService;
import com.kizuna.recruitment.domain.ApplicantSourceType;
import com.kizuna.recruitment.domain.ApplicantStatus;
import com.kizuna.recruitment.domain.ReceptionChannel;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.store.application.StoreActivationService;
import java.lang.reflect.RecordComponent;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ApplicantController.class)
@Import({ApplicantControllerTest.MethodSecurity.class, StoreContext.class})
class ApplicantControllerTest {
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurity {}

  @Autowired MockMvc mvc;
  @MockitoBean ApplicantService service;
  @MockitoBean SystemConfigService configs;
  @MockitoBean StoreExistenceCheck stores;
  @MockitoBean StoreActivationService activation;

  private static TestActor actor() {
    return new TestActor();
  }

  private static class TestActor {
    RequestPostProcessor authorities(GrantedAuthority... authorities) {
      return authorities(List.of(authorities));
    }

    RequestPostProcessor authorities(Collection<? extends GrantedAuthority> authorities) {
      var authentication = new UsernamePasswordAuthenticationToken("actor", "unused", authorities);
      TestSecurityContextHolder.setAuthentication(authentication);
      return request -> {
        request.setUserPrincipal(authentication);
        return request;
      };
    }
  }

  @BeforeEach
  void setup() {
    when(stores.exists(anyLong())).thenReturn(true);
  }

  @Test
  void listUsesSummaryTypeWithoutSensitiveFields() throws Exception {
    var summary =
        new ApplicantSummaryResponse(
            "a",
            "応募者",
            ApplicantStatus.RECEIVED,
            ReceptionChannel.WEB,
            ApplicantSourceType.DIRECT,
            null,
            "担当",
            OffsetDateTime.now(),
            0L);
    when(service.list(any(), any(), any())).thenReturn(new PageImpl<>(List.of(summary)));
    mvc.perform(
            get("/store/applicants")
                .header("X-Role", "store")
                .header("X-Store-ID", "1")
                .with(actor().authorities(new SimpleGrantedAuthority("PERM_RECRUITMENT_VIEW"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].name").value("応募者"))
        .andExpect(jsonPath("$.content[0].phone").doesNotExist())
        .andExpect(jsonPath("$.content[0].address").doesNotExist())
        .andExpect(jsonPath("$.content[0].interview").doesNotExist());
    assertThat(
            Arrays.stream(ApplicantSummaryResponse.class.getRecordComponents())
                .map(RecordComponent::getName))
        .containsExactly(
            "id",
            "name",
            "status",
            "channel",
            "sourceType",
            "sourceMedia",
            "assignee",
            "createdAt",
            "version");
  }

  @Test
  void castPermissionCannotReadApplicantOrHistory() throws Exception {
    for (String path : List.of("", "/a", "/a/history", "/policy")) {
      mvc.perform(
              get("/store/applicants" + path)
                  .header("X-Role", "store")
                  .header("X-Store-ID", "1")
                  .with(actor().authorities(new SimpleGrantedAuthority("PERM_CAST_MANAGE"))))
          .andExpect(status().isForbidden());
    }
    verifyNoInteractions(service);
  }

  @Test
  void writeRequiresBothReadAndManage() throws Exception {
    String body = "{\"name\":\"応募者\",\"channel\":\"WEB\",\"source_type\":\"DIRECT\"}";
    for (String permission :
        List.of("PERM_RECRUITMENT_VIEW", "PERM_RECRUITMENT_MANAGE", "PERM_CAST_MANAGE")) {
      mvc.perform(
              post("/store/applicants")
                  .with(csrf())
                  .header("X-Role", "store")
                  .header("X-Store-ID", "1")
                  .contentType("application/json")
                  .content(body)
                  .with(actor().authorities(new SimpleGrantedAuthority(permission))))
          .andExpect(status().isForbidden());
    }
    mvc.perform(
            post("/store/applicants")
                .with(csrf())
                .header("X-Role", "store")
                .header("X-Store-ID", "1")
                .contentType("application/json")
                .content(body)
                .with(
                    actor()
                        .authorities(
                            new SimpleGrantedAuthority("PERM_RECRUITMENT_VIEW"),
                            new SimpleGrantedAuthority("PERM_RECRUITMENT_MANAGE"))))
        .andExpect(status().isCreated());
  }

  @Test
  void manageCannotDecideAndDecisionAloneCannotRead() throws Exception {
    for (List<String> permissions :
        List.of(
            List.of("PERM_RECRUITMENT_VIEW", "PERM_RECRUITMENT_MANAGE"),
            List.of("PERM_RECRUITMENT_DECIDE"))) {
      mvc.perform(
              post("/store/applicants/a/decision")
                  .with(csrf())
                  .header("X-Role", "store")
                  .header("X-Store-ID", "1")
                  .contentType("application/json")
                  .content("{\"version\":0,\"status\":\"HIRED\",\"reason\":\"確認\"}")
                  .with(
                      actor()
                          .authorities(
                              permissions.stream().map(SimpleGrantedAuthority::new).toList())))
          .andExpect(status().isForbidden());
    }
    verifyNoInteractions(service);
  }

  @Test
  void updateRequiresVersionAndPolicyExposesUnconfiguredValues() throws Exception {
    mvc.perform(
            put("/store/applicants/a")
                .with(csrf())
                .header("X-Role", "store")
                .header("X-Store-ID", "1")
                .contentType("application/json")
                .content(
                    "{\"intake\":{\"name\":\"応募者\",\"channel\":\"WEB\",\"source_type\":\"DIRECT\"}}")
                .with(
                    actor()
                        .authorities(
                            new SimpleGrantedAuthority("PERM_RECRUITMENT_VIEW"),
                            new SimpleGrantedAuthority("PERM_RECRUITMENT_MANAGE"))))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/store/applicants/policy")
                .header("X-Role", "store")
                .header("X-Store-ID", "1")
                .with(actor().authorities(new SimpleGrantedAuthority("PERM_RECRUITMENT_VIEW"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.final_decision_configured").value(false))
        .andExpect(jsonPath("$.retention_periods").isEmpty());
  }
}
