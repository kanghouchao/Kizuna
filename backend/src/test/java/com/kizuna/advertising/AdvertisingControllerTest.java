package com.kizuna.advertising;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.advertising.api.dto.AdvertisingResponses.CostResponse;
import com.kizuna.advertising.api.store.AdvertisingCostController;
import com.kizuna.advertising.api.store.AdvertisingMediaController;
import com.kizuna.advertising.api.store.AdvertisingOrderCostController;
import com.kizuna.advertising.application.AdvertisingExportService;
import com.kizuna.advertising.application.AdvertisingMediaService;
import com.kizuna.advertising.application.AdvertisingOrderCostService;
import com.kizuna.advertising.application.AdvertisingService;
import com.kizuna.advertising.domain.AdvertisingCategory;
import com.kizuna.advertising.domain.AdvertisingMediaReport;
import com.kizuna.advertising.domain.AdvertisingOrderCostReport;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.store.application.StoreActivationService;
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
  AdvertisingOrderCostController.class
})
@Import({AdvertisingControllerTest.MethodSecurity.class, StoreContext.class})
class AdvertisingControllerTest {
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurity {}

  @Autowired MockMvc mvc;
  @MockitoBean AdvertisingService service;
  @MockitoBean AdvertisingMediaService media;
  @MockitoBean AdvertisingOrderCostService orderCosts;
  @MockitoBean AdvertisingExportService exports;
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
  void creationPreservesRequiredNullFields() throws Exception {
    var at = OffsetDateTime.now();
    when(service.create(any(), anyString()))
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
                post("/store/advertising-costs")
                    .header("X-Role", "store")
                    .header("X-Store-ID", "1")
                    .with(permissions("VIEW", "MANAGE"))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.amount").value(0))
            .andReturn();
    assertThat(result.getResponse().getContentAsString())
        .contains("\"inquiry_count\":null", "\"agency_name\":null", "\"plan_name\":null");
  }

  @Test
  void independentPermissionChecksRejectDirectMutationsAndExport() throws Exception {
    mvc.perform(
            post("/store/advertising-costs")
                .header("X-Role", "store")
                .header("X-Store-ID", "1")
                .with(permissions("VIEW"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/store/advertising-costs")
                .header("X-Role", "store")
                .header("X-Store-ID", "1")
                .with(permissions("MANAGE"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/store/advertising-costs/exports")
                .param("month", "2026-09")
                .param("format", "csv")
                .header("X-Role", "store")
                .header("X-Store-ID", "1")
                .with(permissions("VIEW")))
        .andExpect(status().isForbidden());
    verifyNoInteractions(service, exports);
  }

  @Test
  void rejectsFractionalStringOverflowAndMissingReplacementKeys() throws Exception {
    for (String amount : List.of("1.5", "\"1\"", "2147483648", "-1"))
      mvc.perform(
              post("/store/advertising-costs")
                  .header("X-Role", "store")
                  .header("X-Store-ID", "1")
                  .with(permissions("VIEW", "MANAGE"))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body().replace("\"amount\":0", "\"amount\":" + amount)))
          .andExpect(status().isBadRequest());
    mvc.perform(
            put("/store/advertising-costs/c")
                .header("X-Role", "store")
                .header("X-Store-ID", "1")
                .with(permissions("VIEW", "MANAGE"))
                .with(csrf())
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
    when(media.view("2026-09")).thenReturn(snapshot);
    var query =
        get("/store/advertising-media-summaries")
            .param("month", "2026-09")
            .header("X-Role", "store")
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
    mvc.perform(query.param("page", "2147483647").param("size", "1000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rows.content").isEmpty())
        .andExpect(jsonPath("$.rows.size").value(100));
    for (var invalid :
        List.of("page=-1", "page=0.1", "size=0", "size=", "sort=amount", "month=2026-09")) {
      var pair = invalid.split("=", -1);
      mvc.perform(
              get("/store/advertising-media-summaries")
                  .param("month", "2026-09")
                  .param(pair[0], pair[1])
                  .header("X-Role", "store")
                  .header("X-Store-ID", "1")
                  .with(permissions("VIEW")))
          .andExpect(status().isBadRequest());
    }
    mvc.perform(
            get("/store/advertising-media-summaries")
                .header("X-Role", "store")
                .header("X-Store-ID", "1")
                .with(permissions("VIEW")))
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
              get("/store/advertising-media-summaries/exports")
                  .param("month", "2026-09")
                  .param("format", "csv")
                  .header("X-Role", "store")
                  .header("X-Store-ID", "1")
                  .with(permissions(permissions)))
          .andExpect(status().isForbidden());
    }
    mvc.perform(
            get("/store/advertising-media-summaries/exports")
                .param("month", "2026-09")
                .param("format", "csv")
                .param("page", "0")
                .header("X-Role", "store")
                .header("X-Store-ID", "1")
                .with(permissions("VIEW", "EXPORT")))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/store/advertising-media-summaries")
                .param("month", "2026-09")
                .with(permissions("VIEW")))
        .andExpect(status().isForbidden());
    verifyNoInteractions(exports, media);
  }

  @Test
  void deleteHasNoResponseBody() throws Exception {
    mvc.perform(
            delete("/store/advertising-costs/c")
                .header("X-Role", "store")
                .header("X-Store-ID", "1")
                .with(permissions("VIEW", "MANAGE"))
                .with(csrf())
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
  void orderCostsKeepMissingAmountsAndRatiosAndRejectInvalidPaging() throws Exception {
    var report =
        AdvertisingOrderCostReport.aggregate(
            List.of(), List.of(new AdvertisingOrderCostReport.Order("注文だけ", true)));
    when(orderCosts.view("2026-09"))
        .thenReturn(
            new AdvertisingOrderCostService.Snapshot(
                1, "2026-09", 0, OffsetDateTime.now(), report));
    var result =
        mvc.perform(
                get("/store/advertising-order-costs")
                    .param("month", "2026-09")
                    .header("X-Role", "store")
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
            get("/store/advertising-order-costs")
                .param("month", "2026-09")
                .param("page", "2147483647")
                .param("size", "1000")
                .header("X-Role", "store")
                .header("X-Store-ID", "1")
                .with(orderPermissions("ADVERTISING_COST_VIEW", "ORDER_MANAGE")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rows.content").isEmpty())
        .andExpect(jsonPath("$.rows.size").value(100));
    for (var invalid :
        List.of(
            "page=-1", "page=0.1", "size=0", "page=2147483648", "sort=amount", "month=2026-09")) {
      var pair = invalid.split("=", -1);
      mvc.perform(
              get("/store/advertising-order-costs")
                  .param("month", "2026-09")
                  .param(pair[0], pair[1])
                  .header("X-Role", "store")
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
      for (var path :
          List.of("/store/advertising-order-costs", "/store/advertising-order-costs/exports")) {
        mvc.perform(
                get(path)
                    .param("month", "2026-09")
                    .param("format", "csv")
                    .header("X-Role", "store")
                    .header("X-Store-ID", "1")
                    .with(orderPermissions(codes)))
            .andExpect(status().isForbidden());
      }
    }
    mvc.perform(
            get("/store/advertising-order-costs/exports")
                .param("month", "2026-09")
                .param("format", "csv")
                .header("X-Role", "store")
                .header("X-Store-ID", "1")
                .with(orderPermissions("ADVERTISING_COST_VIEW", "ORDER_MANAGE")))
        .andExpect(status().isForbidden());
    when(exports.exportOrderCost("2026-09", "csv")).thenReturn(new byte[] {1});
    mvc.perform(
            get("/store/advertising-order-costs/exports")
                .param("month", "2026-09")
                .param("format", "csv")
                .header("X-Role", "store")
                .header("X-Store-ID", "1")
                .with(
                    orderPermissions(
                        "ADVERTISING_COST_VIEW", "ORDER_MANAGE", "ADVERTISING_COST_EXPORT")))
        .andExpect(status().isOk());
    mvc.perform(
            get("/store/advertising-order-costs/exports")
                .param("month", "2026-09")
                .param("format", "csv")
                .param("page", "0")
                .header("X-Role", "store")
                .header("X-Store-ID", "1")
                .with(
                    orderPermissions(
                        "ADVERTISING_COST_VIEW", "ORDER_MANAGE", "ADVERTISING_COST_EXPORT")))
        .andExpect(status().isBadRequest());
  }
}
