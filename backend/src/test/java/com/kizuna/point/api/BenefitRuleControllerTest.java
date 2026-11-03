package com.kizuna.point.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.point.api.dto.BenefitRuleMapperImpl;
import com.kizuna.point.application.BenefitRuleService;
import com.kizuna.point.domain.BenefitRule;
import com.kizuna.point.domain.BenefitRuleDefinition;
import com.kizuna.point.domain.BenefitRuleRepeatPolicy;
import com.kizuna.point.domain.BenefitRuleType;
import com.kizuna.point.domain.InvalidBenefitRuleException;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.user.application.StoreScopeResolver;
import com.kizuna.user.domain.StoreScopeType;
import java.util.List;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BenefitRuleController.class)
@Import({
  BenefitRuleControllerTest.MethodSecurityConfig.class,
  StoreScopeHolder.class,
  BenefitRuleMapperImpl.class
})
class BenefitRuleControllerTest {

  /** テスト用にメソッドセキュリティ（@PreAuthorize）を有効化する設定 */
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  @Autowired private MockMvc mockMvc;

  @MockitoBean private BenefitRuleService benefitRuleService;

  // StoreScopeInstallingInterceptor と CurrentStoreArgumentResolver の依存。
  // @WebMvcTest は interceptor と引数解決器を取り込むため、ポートのモックを用意する。
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean StoreScopeResolver storeScopeResolver;

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
        .thenReturn(new PageImpl<>(List.of(rule(1L, "来店ボーナス", true))));

    mockMvc
        .perform(get("/benefit-rules"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].name").value("来店ボーナス"))
        .andExpect(jsonPath("$.content[0].store_count").value(0));
  }

  @Test
  @DisplayName("停用済みの規則も一覧に並ぶこと（削除の口が無いため）")
  @WithMockUser(authorities = "PERM_BENEFIT_MANAGE")
  void listIncludesDeactivatedRules() throws Exception {
    when(benefitRuleService.list(any()))
        .thenReturn(new PageImpl<>(List.of(rule(2L, "終了した施策", false))));

    mockMvc
        .perform(get("/benefit-rules"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].enabled").value(false));
  }

  @Test
  @DisplayName("規則の作成は 201 と生成された規則を返すこと")
  @WithMockUser(authorities = "PERM_BENEFIT_MANAGE")
  void createReturnsCreated() throws Exception {
    when(benefitRuleService.create(any(), any())).thenReturn(rule(9L, "来店ボーナス", true));

    mockMvc
        .perform(
            post("/benefit-rules").contentType(MediaType.APPLICATION_JSON).content(VISIT_RULE_BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(9));

    ArgumentCaptor<BenefitRuleDefinition> captor =
        ArgumentCaptor.forClass(BenefitRuleDefinition.class);
    verify(benefitRuleService).create(eq(BenefitRuleType.VISIT), captor.capture());
    assertThat(captor.getValue().points()).isEqualTo(500);
  }

  @Test
  @DisplayName("目録に無い種別は束縛の段階で 400 になること")
  @WithMockUser(authorities = "PERM_BENEFIT_MANAGE")
  void unknownTypeIsRejected() throws Exception {
    mockMvc
        .perform(
            post("/benefit-rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"謎の特典","type":"BIRTHDAY","store_scope_type":"ALL_STORES",
                     "repeat_policy":"EVERY_TIME","points":500}
                    """))
        .andExpect(status().isBadRequest());

    verify(benefitRuleService, never()).create(any(), any());
  }

  @Test
  @DisplayName("更新の要求に種別を載せると 400 になること（種別は作成後に動かない）")
  @WithMockUser(authorities = "PERM_BENEFIT_MANAGE")
  void updateRejectsTypeField() throws Exception {
    mockMvc
        .perform(
            put("/benefit-rules/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"来店ボーナス","type":"LOGIN","store_scope_type":"ALL_STORES",
                     "repeat_policy":"EVERY_TIME","points":500,"version":0}
                    """))
        .andExpect(status().isBadRequest());

    verify(benefitRuleService, never()).update(any(), any(), any());
  }

  @Test
  @DisplayName("version の無い更新要求は 400 になること（全量置換の上書き事故を入口で塞ぐ）")
  void updateRequiresVersion() throws Exception {
    mockMvc
        .perform(
            put("/benefit-rules/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"来店ボーナス","store_scope_type":"ALL_STORES",
                     "repeat_policy":"EVERY_TIME","points":500}
                    """))
        .andExpect(status().isBadRequest());

    verify(benefitRuleService, never()).update(any(), any(), any());
  }

  @Test
  @DisplayName("規則の更新は 200 と更新後の規則を返すこと")
  @WithMockUser(authorities = "PERM_BENEFIT_MANAGE")
  void updateReturnsOk() throws Exception {
    when(benefitRuleService.update(eq(1L), eq(0L), any())).thenReturn(rule(1L, "来店ボーナス", true));

    mockMvc
        .perform(
            put("/benefit-rules/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"来店ボーナス","store_scope_type":"ALL_STORES",
                     "repeat_policy":"EVERY_TIME","points":500,"version":0}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1));

    ArgumentCaptor<BenefitRuleDefinition> captor =
        ArgumentCaptor.forClass(BenefitRuleDefinition.class);
    verify(benefitRuleService).update(eq(1L), eq(0L), captor.capture());
    assertThat(captor.getValue().name()).isEqualTo("来店ボーナス");
  }

  @Test
  @DisplayName("停用は 204 を返し、二度目はドメイン例外で 400 になること")
  @WithMockUser(authorities = "PERM_BENEFIT_MANAGE")
  void deactivationReturnsNoContentAndRejectsSecondTime() throws Exception {
    mockMvc
        .perform(
            post("/benefit-rules/1/deactivation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0}"))
        .andExpect(status().isNoContent());

    org.mockito.Mockito.doThrow(new InvalidBenefitRuleException("停用済みの規則です"))
        .when(benefitRuleService)
        .deactivate(eq(1L), any());

    mockMvc
        .perform(
            post("/benefit-rules/1/deactivation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("version の無い停用要求は 400 になること（見ていない規則を消させない）")
  void deactivationRequiresVersion() throws Exception {
    mockMvc
        .perform(
            post("/benefit-rules/1/deactivation")
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
    assertThat(BenefitRuleController.class.getDeclaredMethods())
        .noneMatch(
            method ->
                method.isAnnotationPresent(
                    org.springframework.web.bind.annotation.DeleteMapping.class));
  }

  @Test
  @DisplayName("BENEFIT_MANAGE 権限が無ければ規則管理の全端点が 403 を返すこと")
  @WithMockUser(authorities = "PERM_SYSTEM_CONFIG_MANAGE")
  void everyEndpointRejectsWithoutAuthority() throws Exception {
    mockMvc.perform(get("/benefit-rules")).andExpect(status().isForbidden());
    mockMvc.perform(get("/benefit-rules/1")).andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/benefit-rules").contentType(MediaType.APPLICATION_JSON).content(VISIT_RULE_BODY))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            put("/benefit-rules/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"来店ボーナス","store_scope_type":"ALL_STORES",
                     "repeat_policy":"EVERY_TIME","points":500,"version":0}
                    """))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/benefit-rules/1/deactivation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0}"))
        .andExpect(status().isForbidden());
  }

  private static BenefitRule rule(Long id, String name, boolean enabled) {
    var rule =
        BenefitRule.define(
            BenefitRuleType.VISIT,
            BenefitRuleDefinition.builder()
                .name(name)
                .storeScopeType(StoreScopeType.ALL_STORES)
                .repeatPolicy(BenefitRuleRepeatPolicy.EVERY_TIME)
                .points(500)
                .build());
    if (!enabled) rule.deactivate();
    ReflectionTestUtils.setField(rule, "id", id);
    ReflectionTestUtils.setField(rule, "version", 0L);
    return rule;
  }
}
