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
import com.kizuna.advertising.application.AdvertisingExportService;
import com.kizuna.advertising.application.AdvertisingService;
import com.kizuna.advertising.domain.AdvertisingCategory;
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

@WebMvcTest(AdvertisingCostController.class)
@Import({AdvertisingControllerTest.MethodSecurity.class, StoreContext.class})
class AdvertisingControllerTest {
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurity {}

  @Autowired MockMvc mvc;
  @MockitoBean AdvertisingService service;
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
}
