package com.kizuna.recruitment.api;

import static com.kizuna.shared.web.JwtRequests.jwtUser;
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

import com.kizuna.recruitment.api.dto.ApplicantSummaryResponse;
import com.kizuna.recruitment.application.ApplicantCommands;
import com.kizuna.recruitment.application.ApplicantService;
import com.kizuna.recruitment.application.ApplicantViews;
import com.kizuna.recruitment.domain.ApplicantIntake;
import com.kizuna.recruitment.domain.ApplicantSourceType;
import com.kizuna.recruitment.domain.ApplicantStatus;
import com.kizuna.recruitment.domain.ApplicantStatusHistory;
import com.kizuna.recruitment.domain.ReceptionChannel;
import com.kizuna.settings.application.ApplicantRetentionSettings;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.user.application.StoreScopeResolver;
import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
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
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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

  // 応答への変換は controller が行うので、書き込みと詳細の呼出には service の結果が要る。
  static final ApplicantViews.Detail DETAIL =
      new ApplicantViews.Detail(
          "a",
          "応募者",
          ApplicantStatus.RECEIVED,
          ReceptionChannel.WEB,
          ApplicantSourceType.DIRECT,
          null,
          null,
          OffsetDateTime.parse("2026-11-01T00:00:00+09:00"),
          0L,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          9L,
          OffsetDateTime.parse("2026-11-01T00:00:00+09:00"),
          null,
          null);

  @BeforeEach
  void setup() {
    when(stores.exists(anyLong())).thenReturn(true);
    when(storeScopeResolver.resolve(any())).thenReturn(new StoreScope(Set.of(1L, 7L)));
    when(service.get(anyLong(), any())).thenReturn(DETAIL);
    when(service.create(anyLong(), any(), anyLong())).thenReturn(DETAIL);
    when(service.decide(anyLong(), any(), any(), anyLong())).thenReturn(DETAIL);
    when(service.enroll(anyLong(), any(), any(), anyLong())).thenReturn(DETAIL);
  }

  @Test
  void historyIsAPageNewestFirst() throws Exception {
    var pageable = ArgumentCaptor.forClass(Pageable.class);
    when(service.history(eq(1L), eq("a"), pageable.capture()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
    mvc.perform(
            get("/applicants/a/history")
                .header("X-Store-ID", "1")
                .with(jwtUser(42L, "PERM_RECRUITMENT_VIEW")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));
    assertThat(pageable.getValue().getSort())
        .isEqualTo(
            Sort.by(
                Sort.Direction.DESC,
                ApplicantStatusHistory::getCreatedAt,
                ApplicantStatusHistory::getId));
  }

  @Test
  void listUsesSummaryTypeWithoutSensitiveFields() throws Exception {
    var summary =
        new ApplicantViews.Summary(
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
                .with(jwtUser(42L, "PERM_RECRUITMENT_VIEW")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].name").value("応募者"))
        .andExpect(jsonPath("$.content[0].enrollment_id").value("e1"))
        .andExpect(jsonPath("$.content[0].anonymized_at").value("2026-10-29T15:00:00Z"))
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
            "medium",
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
                .with(jwtUser(42L, "PERM_RECRUITMENT_VIEW")))
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
                .with(jwtUser(42L, "PERM_RECRUITMENT_VIEW", "PERM_RECRUITMENT_MANAGE")))
        .andExpect(status().isCreated());
    verify(service).create(eq(7L), any(ApplicantIntake.class), eq(42L));
  }

  @Test
  void castPermissionCannotReadApplicantOrHistory() throws Exception {
    for (String path : List.of("", "/a", "/a/history", "/policy")) {
      mvc.perform(
              get("/applicants" + path)
                  .header("X-Store-ID", "1")
                  .with(jwtUser(42L, "PERM_CAST_MANAGE")))
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
                  .with(jwtUser(42L, permission)))
          .andExpect(status().isForbidden());
    }
    mvc.perform(
            post("/applicants")
                .header("X-Store-ID", "1")
                .contentType("application/json")
                .content(body)
                .with(jwtUser(42L, "PERM_RECRUITMENT_VIEW", "PERM_RECRUITMENT_MANAGE")))
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
                  .with(jwtUser(42L, permissions.toArray(String[]::new))))
          .andExpect(status().isForbidden());
    }
    verifyNoInteractions(service);
    mvc.perform(
            post("/applicants/a/decision")
                .header("X-Store-ID", "7")
                .contentType("application/json")
                .content("{\"version\":0,\"status\":\"HIRED\",\"reason\":\"確認\"}")
                .with(jwtUser(42L, "PERM_RECRUITMENT_VIEW", "PERM_RECRUITMENT_DECIDE")))
        .andExpect(status().isOk());
    verify(service).decide(eq(7L), eq("a"), any(ApplicantCommands.StatusChange.class), eq(42L));
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
                  .with(jwtUser(42L, permissions.toArray(String[]::new))))
          .andExpect(status().isForbidden());
    }
    verifyNoInteractions(service);
    mvc.perform(
            post("/applicants/a/enrollment")
                .header("X-Store-ID", "7")
                .contentType("application/json")
                .content(body)
                .with(jwtUser(42L, "PERM_RECRUITMENT_VIEW", "PERM_CAST_MANAGE")))
        .andExpect(status().isCreated());
    var request = ArgumentCaptor.forClass(ApplicantCommands.Enrollment.class);
    verify(service).enroll(eq(7L), eq("a"), request.capture(), eq(42L));
    assertThat(request.getValue().cast().startedOn()).isEqualTo(LocalDate.of(2026, 11, 1));
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
                  .with(jwtUser(42L, "PERM_RECRUITMENT_VIEW", "PERM_CAST_MANAGE")))
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
                .with(jwtUser(42L, "PERM_RECRUITMENT_VIEW", "PERM_RECRUITMENT_DECIDE")))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  void policyReturnsThreeRetentionPeriodsWithUnsetAsNull() throws Exception {
    when(service.policy()).thenReturn(new ApplicantRetentionSettings(6, null, 0));
    mvc.perform(
            get("/applicants/policy")
                .header("X-Store-ID", "1")
                .with(jwtUser(42L, "PERM_RECRUITMENT_VIEW")))
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
                .with(jwtUser(42L, "PERM_CAST_MANAGE")))
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
                .with(jwtUser(42L, "PERM_RECRUITMENT_VIEW", "PERM_RECRUITMENT_MANAGE")))
        .andExpect(status().isBadRequest());
  }
}
