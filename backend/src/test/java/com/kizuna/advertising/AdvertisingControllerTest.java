package com.kizuna.advertising;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.advertising.api.AdvertisingCostController;
import com.kizuna.advertising.api.AdvertisingMediaController;
import com.kizuna.advertising.api.AdvertisingOrderCostController;
import com.kizuna.advertising.api.AdvertisingRecruitmentEffectController;
import com.kizuna.advertising.api.dto.AdvertisingRecruitmentEffectResponse;
import com.kizuna.advertising.api.dto.AdvertisingResponses.CostResponse;
import com.kizuna.advertising.application.AdvertisingExportService;
import com.kizuna.advertising.application.AdvertisingMediaService;
import com.kizuna.advertising.application.AdvertisingOrderCostService;
import com.kizuna.advertising.application.AdvertisingRecruitmentEffectService;
import com.kizuna.advertising.application.AdvertisingService;
import com.kizuna.advertising.domain.AdvertisingCategory;
import com.kizuna.advertising.domain.AdvertisingMediaReport;
import com.kizuna.advertising.domain.AdvertisingOrderCostReport;
import com.kizuna.advertising.domain.AdvertisingRecruitmentEffectReport;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.user.application.StoreScopeResolver;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest({
  AdvertisingCostController.class,
  AdvertisingMediaController.class,
  AdvertisingOrderCostController.class,
  AdvertisingRecruitmentEffectController.class
})
@Import({AdvertisingControllerTest.MethodSecurity.class, StoreScopeHolder.class})
class AdvertisingControllerTest {
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurity {}

  @Autowired MockMvc mvc;
  @MockitoBean AdvertisingService service;
  @MockitoBean AdvertisingMediaService media;
  @MockitoBean AdvertisingOrderCostService orderCosts;
  @MockitoBean AdvertisingRecruitmentEffectService recruitmentEffects;
  @MockitoBean AdvertisingExportService exports;
  @MockitoBean SystemConfigService configs;
  @MockitoBean StoreExistenceCheck stores;
  @MockitoBean StoreScopeResolver storeScopeResolver;

  private static TestActor actor() {
    return new TestActor();
  }

  private static class TestActor {
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

  private RequestPostProcessor permissions(String... permissions) {
    return actor()
        .authorities(
            Arrays.stream(permissions)
                .map(p -> new SimpleGrantedAuthority("PERM_ADVERTISING_COST_" + p))
                .toList());
  }

  private String body() {
    return "{\"month\":\"2026-09\",\"category\":\"SALES\",\"media_name\":\"媒体\",\"amount\":0,\"inquiry_count\":null,\"request_id\":\"b57cfd30-22c8-4fb7-b4ab-de479d723978\"}";
  }

  @Test
  void changesAreAPageNewestFirstAndRejectOutOfRangePages() throws Exception {
    var pageable = ArgumentCaptor.forClass(Pageable.class);
    when(service.changes(eq(1L), eq("2026-09"), pageable.capture()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

    mvc.perform(
            get("/advertising-cost-months/2026-09/changes")
                .header("X-Store-ID", "1")
                .with(permissions("VIEW")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0));
    assertThat(pageable.getValue().getSort())
        .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt", "id"));

    mvc.perform(
            get("/advertising-cost-months/2026-09/changes")
                .param("page", "99999999")
                .param("size", "100")
                .header("X-Store-ID", "1")
                .with(permissions("VIEW")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void creationPreservesRequiredNullFields() throws Exception {
    var at = OffsetDateTime.now();
    when(service.create(any(), any(), anyString()))
        .thenReturn(
            new CostResponse(
                "c",
                1L,
                "2026-09",
                AdvertisingCategory.SALES,
                "媒体",
                null,
                null,
                null,
                0,
                0,
                at,
                at));
    var result =
        mvc.perform(
                post("/advertising-costs")
                    .header("X-Store-ID", "1")
                    .with(permissions("VIEW", "MANAGE"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.amount").value(0))
            .andReturn();
    assertThat(result.getResponse().getContentAsString())
        .contains("\"inquiry_count\":null", "\"agency_name\":null", "\"plan_name\":null");
  }

  @Test
  void readPassesTheNamedStoreToTheService() throws Exception {
    var at = OffsetDateTime.now();
    when(service.get(7L, "c"))
        .thenReturn(
            new CostResponse(
                "c",
                7L,
                "2026-09",
                AdvertisingCategory.SALES,
                "媒体",
                null,
                null,
                null,
                0,
                0,
                at,
                at));
    mvc.perform(get("/advertising-costs/c").header("X-Store-ID", "7").with(permissions("VIEW")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.store_id").value(7));
    verify(service).get(7L, "c");
  }

  @Test
  void writePassesTheNamedStoreToTheService() throws Exception {
    mvc.perform(
            post("/advertising-costs")
                .header("X-Store-ID", "7")
                .with(permissions("VIEW", "MANAGE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()))
        .andExpect(status().isCreated());
    verify(service).create(eq(7L), any(), eq("actor"));
  }

  @Test
  void exportReadsAndNamesTheFileAfterTheNamedStore() throws Exception {
    when(exports.export(7L, "2026-09", "csv")).thenReturn(new byte[] {1});
    mvc.perform(
            get("/advertising-costs/exports")
                .param("month", "2026-09")
                .param("format", "csv")
                .header("X-Store-ID", "7")
                .with(permissions("VIEW", "EXPORT")))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(
                    "Content-Disposition",
                    "attachment; filename=\"advertising-costs-7-2026-09.csv\""));
    verify(exports).export(7L, "2026-09", "csv");
  }

  @Test
  void independentPermissionChecksRejectDirectMutationsAndExport() throws Exception {
    mvc.perform(
            post("/advertising-costs")
                .header("X-Store-ID", "1")
                .with(permissions("VIEW"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/advertising-costs")
                .header("X-Store-ID", "1")
                .with(permissions("MANAGE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/advertising-costs/exports")
                .param("month", "2026-09")
                .param("format", "csv")
                .header("X-Store-ID", "1")
                .with(permissions("VIEW")))
        .andExpect(status().isForbidden());
    verifyNoInteractions(service, exports);
  }

  @Test
  void rejectsFractionalStringOverflowAndMissingReplacementKeys() throws Exception {
    for (String amount : List.of("1.5", "\"1\"", "2147483648", "-1"))
      mvc.perform(
              post("/advertising-costs")
                  .header("X-Store-ID", "1")
                  .with(permissions("VIEW", "MANAGE"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body().replace("\"amount\":0", "\"amount\":" + amount)))
          .andExpect(status().isBadRequest());
    mvc.perform(
            put("/advertising-costs/c")
                .header("X-Store-ID", "1")
                .with(permissions("VIEW", "MANAGE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"category\":\"SALES\",\"media_name\":\"媒体\",\"amount\":0,\"version\":0,\"reason\":\"修正\",\"request_id\":\"b57cfd30-22c8-4fb7-b4ab-de479d723978\"}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  void mediaReadPreservesNullAndPagesWholeMonthAndRejectsInvalidQueries() throws Exception {
    var snapshot =
        new AdvertisingMediaService.Snapshot(
            1,
            "2026-09",
            1,
            OffsetDateTime.now(),
            AdvertisingMediaReport.aggregate(
                List.of(
                    new AdvertisingMediaReport.Entry(AdvertisingCategory.SALES, "媒体", 0, null))));
    when(media.view(1L, "2026-09")).thenReturn(snapshot);
    var query =
        get("/advertising-media-summaries")
            .param("month", "2026-09")
            .header("X-Store-ID", "1")
            .with(permissions("VIEW"));
    var result =
        mvc.perform(query)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows.content[0].inquiry_status").value("UNRECORDED"))
            .andExpect(jsonPath("$.rows.size").value(20))
            .andReturn();
    assertThat(result.getResponse().getContentAsString())
        .contains("\"recorded_inquiry_count_sum\":null");
    assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
    mvc.perform(
            get("/advertising-media-summaries")
                .param("month", "2026-09")
                .param("size", "1000")
                .header("X-Store-ID", "1")
                .with(permissions("VIEW")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rows.size").value(100));
    mvc.perform(query.param("page", "2147483647").param("size", "1000"))
        .andExpect(status().isBadRequest());
    for (var normalized : List.of("page=-1", "page=0.1", "size=0", "size=")) {
      var pair = normalized.split("=", -1);
      mvc.perform(
              get("/advertising-media-summaries")
                  .param("month", "2026-09")
                  .param(pair[0], pair[1])
                  .header("X-Store-ID", "1")
                  .with(permissions("VIEW")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.rows.number").value(0))
          .andExpect(jsonPath("$.rows.size").value(20));
    }
    for (var invalid : List.of("sort=amount", "month=2026-09", "page=0&page=1")) {
      var request =
          get("/advertising-media-summaries")
              .param("month", "2026-09")
              .header("X-Store-ID", "1")
              .with(permissions("VIEW"));
      for (var param : invalid.split("&")) {
        var pair = param.split("=", -1);
        request.param(pair[0], pair[1]);
      }
      mvc.perform(request).andExpect(status().isBadRequest());
    }
    mvc.perform(
            get("/advertising-media-summaries").header("X-Store-ID", "1").with(permissions("VIEW")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void mediaExportRequiresBothPermissionsAndRejectsPaging() throws Exception {
    for (var permissions :
        List.of(
            new String[] {"VIEW"},
            new String[] {"EXPORT"},
            new String[] {"SET_VIEW", "SET_EXPORT"})) {
      mvc.perform(
              get("/advertising-media-summaries/exports")
                  .param("month", "2026-09")
                  .param("format", "csv")
                  .header("X-Store-ID", "1")
                  .with(permissions(permissions)))
          .andExpect(status().isForbidden());
    }
    mvc.perform(
            get("/advertising-media-summaries/exports")
                .param("month", "2026-09")
                .param("format", "csv")
                .param("page", "0")
                .header("X-Store-ID", "1")
                .with(permissions("VIEW", "EXPORT")))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/advertising-media-summaries").param("month", "2026-09").with(permissions("VIEW")))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(exports, media);
  }

  @Test
  void deleteHasNoResponseBody() throws Exception {
    mvc.perform(
            delete("/advertising-costs/c")
                .header("X-Store-ID", "1")
                .with(permissions("VIEW", "MANAGE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"version\":0,\"reason\":\"誤入力\",\"request_id\":\"b57cfd30-22c8-4fb7-b4ab-de479d723978\"}"))
        .andExpect(status().isNoContent());
  }

  private RequestPostProcessor orderPermissions(String... codes) {
    return actor()
        .authorities(
            Arrays.stream(codes).map(c -> new SimpleGrantedAuthority("PERM_" + c)).toList());
  }

  @Test
  void orderCostsKeepMissingAmountsAndRatiosAndNormalizeOrRejectPaging() throws Exception {
    var report =
        AdvertisingOrderCostReport.aggregate(
            List.of(), List.of(new AdvertisingOrderCostReport.Order("注文だけ", true)));
    when(orderCosts.view(1L, "2026-09"))
        .thenReturn(
            new AdvertisingOrderCostService.Snapshot(
                1, "2026-09", 0, OffsetDateTime.now(), report));
    var result =
        mvc.perform(
                get("/advertising-order-costs")
                    .param("month", "2026-09")
                    .header("X-Store-ID", "1")
                    .with(orderPermissions("ADVERTISING_COST_VIEW", "ORDER_MANAGE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows.size").value(20))
            .andExpect(jsonPath("$.rows.content[0].calculation_status").value("NO_COST_RECORDS"))
            .andReturn();
    assertThat(result.getResponse().getContentAsString())
        .contains("\"recorded_sales_amount\":null", "\"cost_per_order\":null");
    assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
    mvc.perform(
            get("/advertising-order-costs")
                .param("month", "2026-09")
                .param("page", "2147483647")
                .param("size", "1000")
                .header("X-Store-ID", "1")
                .with(orderPermissions("ADVERTISING_COST_VIEW", "ORDER_MANAGE")))
        .andExpect(status().isBadRequest());
    for (var normalized : List.of("page=-1", "page=0.1", "size=0", "page=2147483648")) {
      var pair = normalized.split("=", -1);
      mvc.perform(
              get("/advertising-order-costs")
                  .param("month", "2026-09")
                  .param(pair[0], pair[1])
                  .header("X-Store-ID", "1")
                  .with(orderPermissions("ADVERTISING_COST_VIEW", "ORDER_MANAGE")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.rows.number").value(0))
          .andExpect(jsonPath("$.rows.size").value(20));
    }
    for (var invalid : List.of("sort=amount", "month=2026-09")) {
      var pair = invalid.split("=", -1);
      mvc.perform(
              get("/advertising-order-costs")
                  .param("month", "2026-09")
                  .param(pair[0], pair[1])
                  .header("X-Store-ID", "1")
                  .with(orderPermissions("ADVERTISING_COST_VIEW", "ORDER_MANAGE")))
          .andExpect(status().isBadRequest());
    }
  }

  @Test
  void orderCountsAndExportsRequireBothSourcePermissions() throws Exception {
    for (var codes :
        List.of(
            new String[] {"ADVERTISING_COST_VIEW"},
            new String[] {"ORDER_MANAGE"},
            new String[] {"ADVERTISING_COST_VIEW", "ADVERTISING_COST_EXPORT"})) {
      for (var path : List.of("/advertising-order-costs", "/advertising-order-costs/exports")) {
        mvc.perform(
                get(path)
                    .param("month", "2026-09")
                    .param("format", "csv")
                    .header("X-Store-ID", "1")
                    .with(orderPermissions(codes)))
            .andExpect(status().isForbidden());
      }
    }
    mvc.perform(
            get("/advertising-order-costs/exports")
                .param("month", "2026-09")
                .param("format", "csv")
                .header("X-Store-ID", "1")
                .with(orderPermissions("ADVERTISING_COST_VIEW", "ORDER_MANAGE")))
        .andExpect(status().isForbidden());
    when(exports.exportOrderCost(1L, "2026-09", "csv")).thenReturn(new byte[] {1});
    mvc.perform(
            get("/advertising-order-costs/exports")
                .param("month", "2026-09")
                .param("format", "csv")
                .header("X-Store-ID", "1")
                .with(
                    orderPermissions(
                        "ADVERTISING_COST_VIEW", "ORDER_MANAGE", "ADVERTISING_COST_EXPORT")))
        .andExpect(status().isOk());
    mvc.perform(
            get("/advertising-order-costs/exports")
                .param("month", "2026-09")
                .param("format", "csv")
                .param("page", "0")
                .header("X-Store-ID", "1")
                .with(
                    orderPermissions(
                        "ADVERTISING_COST_VIEW", "ORDER_MANAGE", "ADVERTISING_COST_EXPORT")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void recruitmentEffectsKeepNotComputedRatiosAndRetentionCounts() throws Exception {
    var report =
        AdvertisingRecruitmentEffectReport.aggregate(
            List.of(new AdvertisingRecruitmentEffectReport.Cost("費用だけ", 30000)),
            List.of(
                new AdvertisingRecruitmentEffectReport.Hire("入店だけ", LocalDate.of(2026, 5, 1), null),
                new AdvertisingRecruitmentEffectReport.Hire(null, LocalDate.of(2026, 5, 2), null)),
            LocalDate.of(2026, 7, 15),
            ZoneOffset.UTC);
    when(recruitmentEffects.view(1L, "2026-05"))
        .thenReturn(
            new AdvertisingRecruitmentEffectService.Snapshot(
                1, "2026-05", 0, OffsetDateTime.now(), LocalDate.of(2026, 7, 15), report));
    var result =
        mvc.perform(
                get("/advertising-recruitment-effects")
                    .param("month", "2026-05")
                    .header("X-Store-ID", "1")
                    .with(orderPermissions("ADVERTISING_COST_VIEW", "RECRUITMENT_VIEW")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unnamed_media_hire_count").value(1))
            .andExpect(jsonPath("$.retention_as_of").value("2026-07-15"))
            .andExpect(jsonPath("$.rows.content[0].media_name").value("入店だけ"))
            .andExpect(jsonPath("$.rows.content[0].calculation_status").value("NO_COST_RECORDS"))
            .andExpect(jsonPath("$.rows.content[0].retention[0].months").value(2))
            .andExpect(jsonPath("$.rows.content[0].retention[0].judgeable_count").value(1))
            .andExpect(jsonPath("$.rows.content[0].retention[0].retained_count").value(1))
            .andExpect(jsonPath("$.rows.content[0].retention[1].judgeable_count").value(0))
            .andExpect(jsonPath("$.rows.content[1].calculation_status").value("NO_HIRES"))
            .andReturn();
    assertThat(result.getResponse().getContentAsString())
        .contains("\"recorded_recruitment_amount\":null", "\"cost_per_hire\":null");
    assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
    mvc.perform(
            get("/advertising-recruitment-effects")
                .param("month", "2026-05")
                .param("sort", "media")
                .header("X-Store-ID", "1")
                .with(orderPermissions("ADVERTISING_COST_VIEW", "RECRUITMENT_VIEW")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void recruitmentEffectsRequireBothCostAndRecruitmentPermissions() throws Exception {
    for (var codes :
        List.of(
            new String[] {"ADVERTISING_COST_VIEW"},
            new String[] {"RECRUITMENT_VIEW"},
            new String[] {"ADVERTISING_COST_VIEW", "ORDER_MANAGE"})) {
      mvc.perform(
              get("/advertising-recruitment-effects")
                  .param("month", "2026-05")
                  .header("X-Store-ID", "1")
                  .with(orderPermissions(codes)))
          .andExpect(status().isForbidden());
    }
    verifyNoInteractions(recruitmentEffects);
  }

  @Test
  void recruitmentEffectResponseTypesCarryNoApplicantIdentity() {
    for (var type :
        List.of(
            AdvertisingRecruitmentEffectResponse.class,
            AdvertisingRecruitmentEffectResponse.RecruitmentEffectSummary.class,
            AdvertisingRecruitmentEffectResponse.RetentionSummary.class)) {
      assertThat(
              Arrays.stream(type.getRecordComponents())
                  .map(c -> c.getName().toLowerCase(Locale.ROOT)))
          .noneMatch(
              n ->
                  n.contains("applicant")
                      || n.contains("enrollment")
                      || n.equals("id")
                      || n.equals("name")
                      || n.contains("phone")
                      || n.contains("email")
                      || n.contains("address"));
    }
  }
}
