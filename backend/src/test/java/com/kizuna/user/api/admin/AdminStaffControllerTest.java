package com.kizuna.user.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.user.api.dto.AdminStaffResponse;
import com.kizuna.user.application.AdminStaffService;
import com.kizuna.user.application.StoreScopeResolver;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 一覧の要求パラメータ配線の単体テスト（{@code ?sort=} 上書き時の副キー id 補完と、{@code ?storeId=} の受け渡し）。 */
@WebMvcTest(AdminStaffController.class)
@Import({
  AdminStaffControllerTest.MethodSecurityConfig.class,
  StoreContext.class,
  StoreScopeHolder.class
})
class AdminStaffControllerTest {

  /** テスト用にメソッドセキュリティ（@PreAuthorize）を有効化する設定 */
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  /** Principal 引数の解決は servlet の userPrincipal を読むため、要求へ直接載せる。 */
  private static final java.security.Principal ACTOR = () -> "actor@kizuna.test";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AdminStaffService adminStaffService;

  // MaintenanceModeInterceptor / StoreExistenceInterceptor は HandlerInterceptor として
  // @WebMvcTest に自動で取り込まれるため、その依存もモックで満たす必要がある。
  @MockitoBean private SystemConfigService systemConfigService;
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean StoreScopeResolver storeScopeResolver;

  @Test
  @DisplayName("GET /admin/staff?sort=displayName でも id が副キーとして補われること")
  @WithMockUser(authorities = "PERM_STAFF_MANAGE")
  void listAppendsIdTiebreakerWhenCallerOverridesSort() throws Exception {
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    Page<AdminStaffResponse> empty = new PageImpl<>(List.of());
    when(adminStaffService.list(any(), any(), pageableCaptor.capture(), any())).thenReturn(empty);

    mockMvc
        .perform(get("/admin/staff?sort=displayName").principal(ACTOR))
        .andExpect(status().isOk());

    assertThat(pageableCaptor.getValue().getSort().getOrderFor("id")).isNotNull();
  }

  @Test
  @DisplayName("GET /admin/staff?storeId= が店舗絞り込みとしてサービスへ渡ること")
  @WithMockUser(authorities = "PERM_STAFF_MANAGE")
  void listPassesStoreIdFilterToService() throws Exception {
    ArgumentCaptor<Long> storeIdCaptor = ArgumentCaptor.forClass(Long.class);
    Page<AdminStaffResponse> empty = new PageImpl<>(List.of());
    when(adminStaffService.list(any(), storeIdCaptor.capture(), any(), any())).thenReturn(empty);

    mockMvc.perform(get("/admin/staff?storeId=7").principal(ACTOR)).andExpect(status().isOk());

    assertThat(storeIdCaptor.getValue()).isEqualTo(7L);
  }

  @Test
  @DisplayName("storeId 未指定なら絞り込みなし（null）でサービスへ渡ること")
  @WithMockUser(authorities = "PERM_STAFF_MANAGE")
  void listPassesNullStoreIdWhenParamAbsent() throws Exception {
    ArgumentCaptor<Long> storeIdCaptor = ArgumentCaptor.forClass(Long.class);
    Page<AdminStaffResponse> empty = new PageImpl<>(List.of());
    when(adminStaffService.list(any(), storeIdCaptor.capture(), any(), any())).thenReturn(empty);

    mockMvc.perform(get("/admin/staff").principal(ACTOR)).andExpect(status().isOk());

    assertThat(storeIdCaptor.getValue()).isNull();
  }

  @Test
  @DisplayName("STAFF_CREDENTIAL_MANAGE のみ保持では GET /admin/staff が 403（授権の閲覧・編集は STAFF_MANAGE 門）")
  @WithMockUser(authorities = "PERM_STAFF_CREDENTIAL_MANAGE")
  void accountManageAloneCannotReadGrants() throws Exception {
    mockMvc.perform(get("/admin/staff").principal(ACTOR)).andExpect(status().isForbidden());
  }
}
