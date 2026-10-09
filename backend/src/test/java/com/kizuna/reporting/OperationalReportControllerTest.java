package com.kizuna.reporting;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.advertising.reporting.AdvertisingReportFacts;
import com.kizuna.advertising.reporting.AdvertisingReportReader;
import com.kizuna.order.reporting.OperationalFacts;
import com.kizuna.order.reporting.OperationalReportReader;
import com.kizuna.remuneration.reporting.RemunerationReportFacts;
import com.kizuna.remuneration.reporting.RemunerationReportReader;
import com.kizuna.reporting.api.platform.PlatformOperationalReportController;
import com.kizuna.reporting.api.store.StoreOperationalReportController;
import com.kizuna.reporting.application.OperationalReportService;
import com.kizuna.reporting.application.ReportSnapshot;
import com.kizuna.reporting.infrastructure.ReportRenderer;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.store.application.StoreActivationService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;

@WebMvcTest({StoreOperationalReportController.class, PlatformOperationalReportController.class})
@Import({
  OperationalReportControllerTest.Config.class,
  OperationalReportService.class,
  ReportSnapshot.class,
  ReportRenderer.class,
  StoreContext.class
})
class OperationalReportControllerTest {
  @TestConfiguration
  @EnableMethodSecurity
  @EnableWebSecurity
  static class Config {
    @Bean
    AppProperties appProperties() {
      return new AppProperties();
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
      return http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
    }
  }

  @MockitoBean PlatformTransactionManager transactions;
  @Autowired MockMvc mvc;
  @Autowired AppProperties properties;
  @MockitoBean OperationalReportReader reader;
  @MockitoBean RemunerationReportReader remuneration;
  @MockitoBean AdvertisingReportReader advertising;
  @MockitoBean SystemConfigService systemConfigService;
  @MockitoBean StoreExistenceCheck storeExistenceCheck;
  @MockitoBean StoreActivationService storeActivationService;

  @BeforeEach
  void setup() {
    properties.getOperationalReport().setMaxBytes(16L * 1024 * 1024);
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    var facts =
        new OperationalFacts(
            OffsetDateTime.parse("2026-10-01T12:00:00+09:00"),
            List.of(new OperationalFacts.Store(1L, "日本語店舗")),
            List.of());
    when(remuneration.read(any(), any(), any(), anyInt()))
        .thenReturn(new RemunerationReportFacts(List.of(), List.of()));
    when(advertising.read(anyBoolean(), any(), any(), any(), anyInt()))
        .thenReturn(new AdvertisingReportFacts(List.of()));
    when(reader.store(any(), any())).thenReturn(facts);
    when(reader.platform(any(), any(), any())).thenReturn(facts);
  }

  MockHttpServletRequestBuilder request(String scope, String path, String... permissions) {
    var request =
        get("/" + scope + "/operational-reports" + path)
            .param("from", "2026-09-01")
            .param("to", "2026-09-30")
            .with(
                jwt()
                    .jwt(
                        j ->
                            j.subject("actor")
                                .claim("storeBridge", true)
                                .claim("storeScopeType", "SPECIFIC_STORES")
                                .claim("storeIds", List.of(1L)))
                    .authorities(
                        Arrays.stream(permissions)
                            .map(p -> new SimpleGrantedAuthority("PERM_" + p))
                            .toArray(SimpleGrantedAuthority[]::new)));
    if (scope.equals("store")) request.header("X-Role", "store").header("X-Store-ID", "1");
    return request;
  }

  @Test
  void readAndExportRequireIndependentPermissionsAndScopeAuthority() throws Exception {
    for (String scope : List.of("store", "platform")) {
      String permission = scope.equals("store") ? "ORDER_MANAGE" : "ORDER_SET_MANAGE";
      mvc.perform(request(scope, "", permission, "OPERATIONAL_REPORT_VIEW"))
          .andExpect(status().isOk())
          .andExpect(header().string("Cache-Control", "no-store"))
          .andExpect(jsonPath("$.stores[0].store_name").value("日本語店舗"))
          .andExpect(jsonPath("$.total_fee").value(0));
      mvc.perform(
              request(scope, "/exports", permission, "OPERATIONAL_REPORT_VIEW")
                  .param("format", "csv"))
          .andExpect(status().isForbidden());
      mvc.perform(request(scope, "", "OPERATIONAL_REPORT_VIEW", "OPERATIONAL_REPORT_EXPORT"))
          .andExpect(status().isForbidden());
      mvc.perform(
              request(scope, "/exports", permission, "OPERATIONAL_REPORT_EXPORT")
                  .param("format", "csv"))
          .andExpect(status().isForbidden());
      for (String format : List.of("csv", "xlsx"))
        mvc.perform(
                request(
                        scope,
                        "/exports",
                        permission,
                        "OPERATIONAL_REPORT_VIEW",
                        "OPERATIONAL_REPORT_EXPORT")
                    .param("format", format)
                    .param("page", "99")
                    .param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(
                header()
                    .string(
                        "Content-Disposition",
                        "attachment; filename=\"operational-report." + format + "\""));
    }
  }

  @Test
  void additionalAmountsRequireRemunerationPermissionAndLegacyOmitsFields() throws Exception {
    for (String scope : List.of("store", "platform")) {
      String permission = scope.equals("store") ? "ORDER_MANAGE" : "ORDER_SET_MANAGE";
      mvc.perform(request(scope, "", permission, "OPERATIONAL_REPORT_VIEW"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.remuneration").doesNotExist())
          .andExpect(jsonPath("$.basis").value("completed-orders-current-v1"));
      for (String path : List.of("", "/exports")) {
        mvc.perform(
                request(
                        scope,
                        path,
                        permission,
                        "OPERATIONAL_REPORT_VIEW",
                        "OPERATIONAL_REPORT_EXPORT")
                    .param("include_remuneration", "true")
                    .param("format", "csv"))
            .andExpect(status().isForbidden());
        mvc.perform(
                request(
                        scope,
                        path,
                        permission,
                        "OPERATIONAL_REPORT_VIEW",
                        "OPERATIONAL_REPORT_EXPORT",
                        "REMUNERATION_VIEW")
                    .param("include_remuneration", "true")
                    .param("format", "csv"))
            .andExpect(status().isOk());
      }
      mvc.perform(
              request(scope, "", permission, "OPERATIONAL_REPORT_VIEW", "REMUNERATION_VIEW")
                  .param("include_remuneration", "true"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.remuneration.total").value(0))
          .andExpect(jsonPath("$.basis").value("completed-orders-remuneration-current-v2"));
      mvc.perform(
              request(scope, "/exports", permission, "OPERATIONAL_REPORT_VIEW", "REMUNERATION_VIEW")
                  .param("include_remuneration", "true")
                  .param("format", "csv"))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  void unknownAmountsAreExplicitNullDespiteGlobalNonNullSerialization() throws Exception {
    var day =
        new RemunerationReportFacts.Day(
            1L,
            9L,
            LocalDate.of(2026, 9, 1),
            0,
            null,
            null,
            null,
            null,
            "PT4H",
            false,
            "NOT_CONFIGURED",
            null,
            500);
    when(remuneration.read(any(), any(), any(), anyInt()))
        .thenReturn(new RemunerationReportFacts(List.of(day), List.of()));
    mvc.perform(
            request("store", "", "ORDER_MANAGE", "OPERATIONAL_REPORT_VIEW", "REMUNERATION_VIEW")
                .param("include_remuneration", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.remuneration.guarantee_total").value(Matchers.nullValue()))
        .andExpect(jsonPath("$.remuneration.total").value(Matchers.nullValue()))
        .andExpect(jsonPath("$.remuneration").value(Matchers.hasKey("guarantee_total")))
        .andExpect(jsonPath("$.remuneration").value(Matchers.hasKey("total")))
        .andExpect(jsonPath("$.rows.content[0].remuneration").value(Matchers.hasKey("total")))
        .andExpect(jsonPath("$.remuneration.known_guarantee_total").value(0))
        .andExpect(jsonPath("$.remuneration.bonus_total").value(500));
  }

  @Test
  void advertisingPermissionsAreIndependentForEachConsoleAndExport() throws Exception {
    for (String scope : List.of("store", "platform")) {
      String order = scope.equals("store") ? "ORDER_MANAGE" : "ORDER_SET_MANAGE";
      String view = scope.equals("store") ? "ADVERTISING_COST_VIEW" : "ADVERTISING_COST_SET_VIEW";
      String export =
          scope.equals("store") ? "ADVERTISING_COST_EXPORT" : "ADVERTISING_COST_SET_EXPORT";
      String otherView =
          scope.equals("store") ? "ADVERTISING_COST_SET_VIEW" : "ADVERTISING_COST_VIEW";
      mvc.perform(request(scope, "", order, "OPERATIONAL_REPORT_VIEW"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.advertising").doesNotExist());
      for (String group : List.of("month", "day")) {
        for (String path : List.of("", "/exports")) {
          for (String extra : List.of("REMUNERATION_VIEW", otherView, export)) {
            mvc.perform(
                    request(
                            scope,
                            path,
                            order,
                            "OPERATIONAL_REPORT_VIEW",
                            "OPERATIONAL_REPORT_EXPORT",
                            extra)
                        .param("include_advertising", "true")
                        .param("group_by", group)
                        .param("format", "csv"))
                .andExpect(status().isForbidden());
          }
        }
      }
      mvc.perform(
              request(scope, "", order, "OPERATIONAL_REPORT_VIEW", view)
                  .param("include_advertising", "true")
                  .param("group_by", "month"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.advertising.status").value("NO_RECORDS"))
          .andExpect(jsonPath("$.advertising.entry_count").value(0))
          .andExpect(jsonPath("$.basis").value("completed-orders-advertising-current-v3"));
      mvc.perform(
              request(
                      scope,
                      "/exports",
                      order,
                      "OPERATIONAL_REPORT_VIEW",
                      "OPERATIONAL_REPORT_EXPORT",
                      view)
                  .param("include_advertising", "true")
                  .param("group_by", "month")
                  .param("format", "csv"))
          .andExpect(status().isForbidden());
      for (String format : List.of("csv", "xlsx")) {
        mvc.perform(
                request(
                        scope,
                        "/exports",
                        order,
                        "OPERATIONAL_REPORT_VIEW",
                        "OPERATIONAL_REPORT_EXPORT",
                        view,
                        export)
                    .param("include_advertising", "true")
                    .param("group_by", "month")
                    .param("format", format))
            .andExpect(status().isOk());
      }
      mvc.perform(
              request(scope, "", order, "OPERATIONAL_REPORT_VIEW", view)
                  .param("include_advertising", "true")
                  .param("include_remuneration", "true")
                  .param("group_by", "month"))
          .andExpect(status().isForbidden());
      mvc.perform(
              request(scope, "", order, "OPERATIONAL_REPORT_VIEW", view, "REMUNERATION_VIEW")
                  .param("include_advertising", "true")
                  .param("include_remuneration", "true")
                  .param("group_by", "month"))
          .andExpect(status().isOk())
          .andExpect(
              jsonPath("$.basis").value("completed-orders-remuneration-advertising-current-v3"));
    }
  }

  @Test
  void inapplicableAdvertisingKeepsAllExplicitNullKeysWithoutReadingCosts() throws Exception {
    when(reader.store(any(), any()))
        .thenReturn(
            new OperationalFacts(
                OffsetDateTime.parse("2026-10-01T12:00:00+09:00"),
                List.of(new OperationalFacts.Store(1L, "店舗")),
                List.of(
                    new OperationalFacts.Order(
                        "a", 1L, LocalDate.of(2026, 9, 30), 1, false, 1000, 500))));
    clearInvocations(advertising);
    for (String[] query :
        List.of(
            new String[] {"2026-09-02", "day", "NOT_APPLICABLE_PARTIAL_MONTH"},
            new String[] {"2026-09-01", "day", "NOT_APPLICABLE_DAY_GROUPING"})) {
      mvc.perform(
              request(
                      "store",
                      "",
                      "ORDER_MANAGE",
                      "OPERATIONAL_REPORT_VIEW",
                      "ADVERTISING_COST_VIEW")
                  .with(
                      r -> {
                        r.setParameter("from", query[0]);
                        return r;
                      })
                  .param("group_by", query[1])
                  .param("include_advertising", "true"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.advertising.status").value(query[2]))
          .andExpect(jsonPath("$.advertising").value(Matchers.hasKey("entry_count")))
          .andExpect(jsonPath("$.advertising").value(Matchers.hasKey("sales_amount")))
          .andExpect(jsonPath("$.advertising").value(Matchers.hasKey("recruitment_amount")))
          .andExpect(jsonPath("$.advertising").value(Matchers.hasKey("recorded_total_amount")))
          .andExpect(jsonPath("$.advertising.entry_count").value(Matchers.nullValue()))
          .andExpect(jsonPath("$.advertising.sales_amount").value(Matchers.nullValue()))
          .andExpect(jsonPath("$.advertising.recruitment_amount").value(Matchers.nullValue()))
          .andExpect(jsonPath("$.advertising.recorded_total_amount").value(Matchers.nullValue()))
          .andExpect(
              jsonPath("$.rows.content[0].advertising")
                  .value(Matchers.hasKey("recorded_total_amount")))
          .andExpect(
              jsonPath("$.rows.content[0].advertising.recorded_total_amount")
                  .value(Matchers.nullValue()));
    }
    verifyNoInteractions(advertising);
  }

  @Test
  void invalidCriteriaAndSizeAre400AndOversizeNeverSetsAttachment() throws Exception {
    mvc.perform(
            request("platform", "", "ORDER_SET_MANAGE", "OPERATIONAL_REPORT_VIEW")
                .param("size", "101"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").isString());
    mvc.perform(
            request(
                    "platform",
                    "/exports",
                    "ORDER_SET_MANAGE",
                    "OPERATIONAL_REPORT_VIEW",
                    "OPERATIONAL_REPORT_EXPORT")
                .param("format", "xls"))
        .andExpect(status().isBadRequest());
    properties.getOperationalReport().setMaxBytes(1);
    mvc.perform(
            request(
                    "platform",
                    "/exports",
                    "ORDER_SET_MANAGE",
                    "OPERATIONAL_REPORT_VIEW",
                    "OPERATIONAL_REPORT_EXPORT")
                .param("format", "xlsx"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error").isString())
        .andExpect(header().doesNotExist("Content-Disposition"));
  }
}
