package com.kizuna.service.api.me;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.service.api.dto.OwnConsentRequest;
import com.kizuna.service.application.OwnServiceConditionService;
import com.kizuna.service.domain.ConsentDecision;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.user.application.StoreScopeResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 本人のサービス条件は store_id で指名した店舗を service へ渡すこと。 */
@WebMvcTest(OwnServiceConditionController.class)
@Import({OwnServiceConditionControllerTest.MethodSecurityConfig.class, StoreScopeHolder.class})
@WithMockUser(username = "cast@example.test", roles = "CAST")
class OwnServiceConditionControllerTest {

  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  private static final Long STORE_ID = 7L;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private OwnServiceConditionService service;
  @MockitoBean private SystemConfigService systemConfigService;
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean StoreScopeResolver storeScopeResolver;

  @Test
  @DisplayName("一覧は store_id の店舗で service を呼ぶこと")
  void listPassesTheNamedStore() throws Exception {
    when(service.list(eq(STORE_ID), eq("cast@example.test"), any(), any()))
        .thenReturn(Page.empty());

    mockMvc
        .perform(
            get("/me/service-conditions")
                .param("store_id", STORE_ID.toString())
                .principal(() -> "cast@example.test"))
        .andExpect(status().isOk());

    verify(service).list(STORE_ID, "cast@example.test", null, PageRequest.of(0, 20, Sort.by("id")));
  }

  @Test
  @DisplayName("受諾の決定は store_id の店舗で service を呼ぶこと")
  void decidePassesTheNamedStore() throws Exception {
    mockMvc
        .perform(
            put("/me/service-conditions/s1/consent")
                .param("store_id", STORE_ID.toString())
                .principal(() -> "cast@example.test")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"terms_version": 1, "consent_version": 0, "decision": "ACCEPTED"}
                    """))
        .andExpect(status().isOk());

    verify(service)
        .decide(
            STORE_ID,
            "cast@example.test",
            "s1",
            new OwnConsentRequest(1L, 0L, ConsentDecision.ACCEPTED));
  }
}
