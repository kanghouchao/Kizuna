package com.kizuna.point.api.platform;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.point.api.dto.BenefitRuleCreateRequest;
import com.kizuna.point.api.dto.BenefitRuleResponse;
import com.kizuna.point.api.dto.BenefitRuleSummaryResponse;
import com.kizuna.point.api.dto.BenefitRuleUpdateRequest;
import com.kizuna.point.application.BenefitRuleService;
import com.kizuna.point.domain.InvalidBenefitRuleException;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.store.application.StoreActivationService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlatformBenefitRuleController.class)
@Import({PlatformBenefitRuleControllerTest.MethodSecurityConfig.class, StoreContext.class})
class PlatformBenefitRuleControllerTest {

  /** テスト用にメソッドセキュリティ（@PreAuthorize）を有効化する設定 */
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  @Autowired private MockMvc mockMvc;

  @MockitoBean private BenefitRuleService benefitRuleService;

  // HandlerInterceptor（StoreExistenceInterceptor / MaintenanceModeInterceptor）の依存。
  // @WebMvcTest は interceptor を取り込むため、ポートのモックを用意する。
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean private StoreActivationService storeActivationService;
  @MockitoBean private SystemConfigService systemConfigService;

  private static final String VISIT_RULE_BODY =
      """
      {"name":"来店ボーナス","type":"VISIT","store_scope_type":"ALL_STORES",
       "repeat_policy":"EVERY_TIME","points":500}
      """;

  @Test
  @DisplayName("BENEFIT_MANAGE 権限があれば規則一覧を取得できること")
  @WithMockUser(authorities = "PERM_BENEFIT_MANAGE")
  void listWithAuthority() throws Exception {
    when(benefitRuleService.list(any()))
        .thenReturn(new PageImpl<>(List.of(summary(1L, "来店ボーナス", true))));

    mockMvc
        .perform(get("/platform/benefit-rules"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].name").value("来店ボーナス"))
        .andExpect(jsonPath("$.content[0].store_count").value(0));
  }

  @Test
  @DisplayName("停用済みの規則も一覧に並ぶこと（削除の口が無いため）")
  @WithMockUser(authorities = "PERM_BENEFIT_MANAGE")
  void listIncludesDeactivatedRules() throws Exception {
    when(benefitRuleService.list(any()))
        .thenReturn(new PageImpl<>(List.of(summary(2L, "終了した施策", false))));

    mockMvc
        .perform(get("/platform/benefit-rules"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].enabled").value(false));
  }

  @Test
  @DisplayName("規則の作成は 201 と生成された規則を返すこと")
  @WithMockUser(authorities = "PERM_BENEFIT_MANAGE")
  void createReturnsCreated() throws Exception {
    when(benefitRuleService.create(any())).thenReturn(response(9L));

    mockMvc
        .perform(
            post("/platform/benefit-rules")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(VISIT_RULE_BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(9));

    ArgumentCaptor<BenefitRuleCreateRequest> captor =
        ArgumentCaptor.forClass(BenefitRuleCreateRequest.class);
    verify(benefitRuleService).create(captor.capture());
    org.assertj.core.api.Assertions.assertThat(captor.getValue().getPoints()).isEqualTo(500);
  }

  @Test
  @DisplayName("目録に無い種別は束縛の段階で 400 になること")
  @WithMockUser(authorities = "PERM_BENEFIT_MANAGE")
  void unknownTypeIsRejected() throws Exception {
    mockMvc
        .perform(
            post("/platform/benefit-rules")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"謎の特典","type":"BIRTHDAY","store_scope_type":"ALL_STORES",
                     "repeat_policy":"EVERY_TIME","points":500}
                    """))
        .andExpect(status().isBadRequest());

    verify(benefitRuleService, never()).create(any());
  }

  @Test
  @DisplayName("更新の要求に種別を載せると 400 になること（種別は作成後に動かない）")
  @WithMockUser(authorities = "PERM_BENEFIT_MANAGE")
  void updateRejectsTypeField() throws Exception {
    mockMvc
        .perform(
            put("/platform/benefit-rules/1")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"来店ボーナス","type":"LOGIN","store_scope_type":"ALL_STORES",
                     "repeat_policy":"EVERY_TIME","points":500,"version":0}
                    """))
        .andExpect(status().isBadRequest());

    verify(benefitRuleService, never()).update(any(), any());
  }

  @Test
  @DisplayName("version の無い更新要求は 400 になること（全量置換の上書き事故を入口で塞ぐ）")
  void updateRequiresVersion() throws Exception {
    mockMvc
        .perform(
            put("/platform/benefit-rules/1")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"来店ボーナス","store_scope_type":"ALL_STORES",
                     "repeat_policy":"EVERY_TIME","points":500}
                    """))
        .andExpect(status().isBadRequest());

    verify(benefitRuleService, never()).update(any(), any());
  }

  @Test
  @DisplayName("規則の更新は 200 と更新後の規則を返すこと")
  @WithMockUser(authorities = "PERM_BENEFIT_MANAGE")
  void updateReturnsOk() throws Exception {
    when(benefitRuleService.update(eq(1L), any())).thenReturn(response(1L));

    mockMvc
        .perform(
            put("/platform/benefit-rules/1")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"来店ボーナス","store_scope_type":"ALL_STORES",
                     "repeat_policy":"EVERY_TIME","points":500,"version":0}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1));

    ArgumentCaptor<BenefitRuleUpdateRequest> captor =
        ArgumentCaptor.forClass(BenefitRuleUpdateRequest.class);
    verify(benefitRuleService).update(eq(1L), captor.capture());
    org.assertj.core.api.Assertions.assertThat(captor.getValue().getName()).isEqualTo("来店ボーナス");
  }

  @Test
  @DisplayName("停用は 204 を返し、二度目はドメイン例外で 400 になること")
  @WithMockUser(authorities = "PERM_BENEFIT_MANAGE")
  void deactivationReturnsNoContentAndRejectsSecondTime() throws Exception {
    mockMvc
        .perform(
            post("/platform/benefit-rules/1/deactivation")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0}"))
        .andExpect(status().isNoContent());

    org.mockito.Mockito.doThrow(new InvalidBenefitRuleException("停用済みの規則です"))
        .when(benefitRuleService)
        .deactivate(eq(1L), any());

    mockMvc
        .perform(
            post("/platform/benefit-rules/1/deactivation")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("version の無い停用要求は 400 になること（見ていない規則を消させない）")
  void deactivationRequiresVersion() throws Exception {
    mockMvc
        .perform(
            post("/platform/benefit-rules/1/deactivation")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());

    verify(benefitRuleService, never()).deactivate(any(), any());
  }

  @Test
  @DisplayName("削除の口を宣言しないこと（退場は停用で表す）")
  void noDeleteHandlerIsDeclared() {
    // HTTP で確かめられない — 未対応メソッドは全域ハンドラの兜底に落ちて 500 になり、
    // 「口が無い」ことと「口が壊れている」ことを区別できないため、宣言そのものを見る。
    org.assertj.core.api.Assertions.assertThat(
            PlatformBenefitRuleController.class.getDeclaredMethods())
        .noneMatch(
            method ->
                method.isAnnotationPresent(
                    org.springframework.web.bind.annotation.DeleteMapping.class));
  }

  @Test
  @DisplayName("BENEFIT_MANAGE 権限が無ければ規則管理の全端点が 403 を返すこと")
  @WithMockUser(authorities = "PERM_SYSTEM_CONFIG_MANAGE")
  void everyEndpointRejectsWithoutAuthority() throws Exception {
    mockMvc.perform(get("/platform/benefit-rules")).andExpect(status().isForbidden());
    mockMvc.perform(get("/platform/benefit-rules/1")).andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/platform/benefit-rules")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(VISIT_RULE_BODY))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            put("/platform/benefit-rules/1")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"来店ボーナス","store_scope_type":"ALL_STORES",
                     "repeat_policy":"EVERY_TIME","points":500,"version":0}
                    """))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/platform/benefit-rules/1/deactivation")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0}"))
        .andExpect(status().isForbidden());
  }

  private static BenefitRuleSummaryResponse summary(Long id, String name, boolean enabled) {
    return new BenefitRuleSummaryResponse(
        id,
        name,
        "VISIT",
        "ALL_STORES",
        0,
        null,
        null,
        null,
        "EVERY_TIME",
        500,
        null,
        null,
        enabled,
        0L);
  }

  private static BenefitRuleResponse response(Long id) {
    return new BenefitRuleResponse(
        id,
        "来店ボーナス",
        "VISIT",
        "ALL_STORES",
        Set.of(),
        null,
        null,
        null,
        "EVERY_TIME",
        500,
        null,
        null,
        true,
        0L);
  }
}
