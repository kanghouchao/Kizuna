package com.kizuna.recruitment.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.recruitment.api.dto.ApplicantEnrollmentRequest;
import com.kizuna.recruitment.api.dto.ApplicantIntakeRequest;
import com.kizuna.recruitment.api.dto.ApplicantSummaryResponse;
import com.kizuna.recruitment.api.dto.ApplicantTransitionRequest;
import com.kizuna.recruitment.api.dto.RecruitmentPolicyResponse;
import com.kizuna.recruitment.application.ApplicantService;
import com.kizuna.recruitment.domain.ApplicantSourceType;
import com.kizuna.recruitment.domain.ApplicantStatus;
import com.kizuna.recruitment.domain.ReceptionChannel;
import com.kizuna.settings.application.ApplicantRetentionSettings;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.user.application.StoreScopeResolver;
import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ApplicantController.class)
@Import({ApplicantControllerTest.MethodSecurity.class, StoreScopeHolder.class})
class ApplicantControllerTest {
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurity {}

  @Autowired MockMvc mvc;
  @MockitoBean ApplicantService service;
  @MockitoBean SystemConfigService configs;
  @MockitoBean StoreExistenceCheck stores;
  @MockitoBean StoreScopeResolver storeScopeResolver;

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
  void historyIsAPageNewestFirst() throws Exception {
    var pageable = ArgumentCaptor.forClass(Pageable.class);
    when(service.history(eq(1L), eq("a"), pageable.capture()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
    mvc.perform(
            get("/applicants/a/history")
                .header("X-Store-ID", "1")
                .with(actor().authorities(new SimpleGrantedAuthority("PERM_RECRUITMENT_VIEW"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));
    assertThat(pageable.getValue().getSort())
        .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt", "id"));
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
            0L,
            "e1",
            OffsetDateTime.parse("2026-10-30T00:00:00+09:00"));
    when(service.list(eq(1L), any(), any(), any())).thenReturn(new PageImpl<>(List.of(summary)));
    mvc.perform(
            get("/applicants")
                .header("X-Store-ID", "1")
                .with(actor().authorities(new SimpleGrantedAuthority("PERM_RECRUITMENT_VIEW"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].name").value("応募者"))
        .andExpect(jsonPath("$.content[0].enrollment_id").value("e1"))
        .andExpect(jsonPath("$.content[0].anonymized_at").value("2026-10-30T00:00:00+09:00"))
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
            "version",
            "enrollmentId",
            "anonymizedAt");
  }

  @Test
  void readPassesTheNamedStoreToTheService() throws Exception {
    mvc.perform(
            get("/applicants/a")
                .header("X-Store-ID", "7")
                .with(actor().authorities(new SimpleGrantedAuthority("PERM_RECRUITMENT_VIEW"))))
        .andExpect(status().isOk());
    verify(service).get(7L, "a");
  }

  @Test
  void writePassesTheNamedStoreToTheService() throws Exception {
    mvc.perform(
            post("/applicants")
                .header("X-Store-ID", "7")
                .contentType("application/json")
                .content("{\"name\":\"応募者\",\"channel\":\"WEB\",\"source_type\":\"DIRECT\"}")
                .with(
                    actor()
                        .authorities(
                            new SimpleGrantedAuthority("PERM_RECRUITMENT_VIEW"),
                            new SimpleGrantedAuthority("PERM_RECRUITMENT_MANAGE"))))
        .andExpect(status().isCreated());
    verify(service).create(eq(7L), any(ApplicantIntakeRequest.class), eq("actor"));
  }

  @Test
  void castPermissionCannotReadApplicantOrHistory() throws Exception {
    for (String path : List.of("", "/a", "/a/history", "/policy")) {
      mvc.perform(
              get("/applicants" + path)
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
              post("/applicants")
                  .header("X-Store-ID", "1")
                  .contentType("application/json")
                  .content(body)
                  .with(actor().authorities(new SimpleGrantedAuthority(permission))))
          .andExpect(status().isForbidden());
    }
    mvc.perform(
            post("/applicants")
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
  void decisionRequiresReadAndDecideTogether() throws Exception {
    for (List<String> permissions :
        List.of(
            List.of("PERM_RECRUITMENT_VIEW", "PERM_RECRUITMENT_MANAGE"),
            List.of("PERM_RECRUITMENT_DECIDE"))) {
      mvc.perform(
              post("/applicants/a/decision")
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
    mvc.perform(
            post("/applicants/a/decision")
                .header("X-Store-ID", "7")
                .contentType("application/json")
                .content("{\"version\":0,\"status\":\"HIRED\",\"reason\":\"確認\"}")
                .with(
                    actor()
                        .authorities(
                            new SimpleGrantedAuthority("PERM_RECRUITMENT_VIEW"),
                            new SimpleGrantedAuthority("PERM_RECRUITMENT_DECIDE"))))
        .andExpect(status().isOk());
    verify(service).decide(eq(7L), eq("a"), any(ApplicantTransitionRequest.class), eq("actor"));
  }

  @Test
  void enrollmentRequiresReadAndCastManageTogether() throws Exception {
    String body = "{\"version\":0,\"name\":\"花\",\"started_on\":\"2026-11-01\"}";
    for (List<String> permissions :
        List.of(
            List.of("PERM_RECRUITMENT_VIEW", "PERM_RECRUITMENT_MANAGE", "PERM_RECRUITMENT_DECIDE"),
            List.of("PERM_CAST_MANAGE"))) {
      mvc.perform(
              post("/applicants/a/enrollment")
                  .header("X-Store-ID", "1")
                  .contentType("application/json")
                  .content(body)
                  .with(
                      actor()
                          .authorities(
                              permissions.stream().map(SimpleGrantedAuthority::new).toList())))
          .andExpect(status().isForbidden());
    }
    verifyNoInteractions(service);
    mvc.perform(
            post("/applicants/a/enrollment")
                .header("X-Store-ID", "7")
                .contentType("application/json")
                .content(body)
                .with(
                    actor()
                        .authorities(
                            new SimpleGrantedAuthority("PERM_RECRUITMENT_VIEW"),
                            new SimpleGrantedAuthority("PERM_CAST_MANAGE"))))
        .andExpect(status().isCreated());
    var request = ArgumentCaptor.forClass(ApplicantEnrollmentRequest.class);
    verify(service).enroll(eq(7L), eq("a"), request.capture(), eq("actor"));
    assertThat(request.getValue().startedOn()).isEqualTo(LocalDate.of(2026, 11, 1));
  }

  @Test
  void enrollmentRequiresVersionNameAndStartedOn() throws Exception {
    for (String body :
        List.of(
            "{\"name\":\"花\",\"started_on\":\"2026-11-01\"}",
            "{\"version\":0,\"name\":\" \",\"started_on\":\"2026-11-01\"}",
            "{\"version\":0,\"name\":\"花\"}",
            "{\"version\":0,\"name\":\"花\",\"started_on\":\"2026-11-01\",\"status\":\"WITHDRAWN\"}"))
      mvc.perform(
              post("/applicants/a/enrollment")
                  .header("X-Store-ID", "1")
                  .contentType("application/json")
                  .content(body)
                  .with(
                      actor()
                          .authorities(
                              new SimpleGrantedAuthority("PERM_RECRUITMENT_VIEW"),
                              new SimpleGrantedAuthority("PERM_CAST_MANAGE"))))
          .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  void decisionRequiresReason() throws Exception {
    mvc.perform(
            post("/applicants/a/decision")
                .header("X-Store-ID", "1")
                .contentType("application/json")
                .content("{\"version\":0,\"status\":\"REJECTED\",\"reason\":\" \"}")
                .with(
                    actor()
                        .authorities(
                            new SimpleGrantedAuthority("PERM_RECRUITMENT_VIEW"),
                            new SimpleGrantedAuthority("PERM_RECRUITMENT_DECIDE"))))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  void policyReturnsThreeRetentionPeriodsWithUnsetAsNull() throws Exception {
    when(service.policy())
        .thenReturn(RecruitmentPolicyResponse.from(new ApplicantRetentionSettings(6, null, 0)));
    mvc.perform(
            get("/applicants/policy")
                .header("X-Store-ID", "1")
                .with(actor().authorities(new SimpleGrantedAuthority("PERM_RECRUITMENT_VIEW"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.final_decision_configured").doesNotExist())
        .andExpect(jsonPath("$.retention_periods.rejected_months").value(6))
        .andExpect(jsonPath("$.retention_periods.withdrawn_months").value(nullValue()))
        .andExpect(jsonPath("$.retention_periods", hasKey("withdrawn_months")))
        .andExpect(jsonPath("$.retention_periods.hired_months").value(0));
  }

  @Test
  void policyRequiresRecruitmentView() throws Exception {
    mvc.perform(
            get("/applicants/policy")
                .header("X-Store-ID", "1")
                .with(actor().authorities(new SimpleGrantedAuthority("PERM_CAST_MANAGE"))))
        .andExpect(status().isForbidden());
    verifyNoInteractions(service);
  }

  @Test
  void updateRequiresVersion() throws Exception {
    mvc.perform(
            put("/applicants/a")
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
  }
}
