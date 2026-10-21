package com.kizuna.user.api.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.user.api.dto.PlatformStaffResponse;
import com.kizuna.user.application.PlatformStaffService;
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
@WebMvcTest(PlatformStaffController.class)
@Import({
  PlatformStaffControllerTest.MethodSecurityConfig.class,
  StoreContext.class,
  StoreScopeHolder.class
})
class PlatformStaffControllerTest {

  /** テスト用にメソッドセキュリティ（@PreAuthorize）を有効化する設定 */
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  /** Principal 引数の解決は servlet の userPrincipal を読むため、要求へ直接載せる。 */
  private static final java.security.Principal ACTOR = () -> "actor@kizuna.test";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PlatformStaffService platformStaffService;

  // MaintenanceModeInterceptor / StoreExistenceInterceptor は HandlerInterceptor として
  // @WebMvcTest に自動で取り込まれるため、その依存もモックで満たす必要がある。
  @MockitoBean private SystemConfigService systemConfigService;
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean StoreScopeResolver storeScopeResolver;

  @Test
  @DisplayName("GET /platform/staff?sort=displayName でも id が副キーとして補われること")
  @WithMockUser(authorities = "PERM_STORE_STAFF_MANAGE")
  void listAppendsIdTiebreakerWhenCallerOverridesSort() throws Exception {
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    Page<PlatformStaffResponse> empty = new PageImpl<>(List.of());
    when(platformStaffService.list(any(), any(), pageableCaptor.capture(), any()))
        .thenReturn(empty);

    mockMvc
        .perform(get("/platform/staff?sort=displayName").principal(ACTOR))
        .andExpect(status().isOk());

    assertThat(pageableCaptor.getValue().getSort().getOrderFor("id")).isNotNull();
  }

  @Test
  @DisplayName("GET /platform/staff?storeId= が店舗絞り込みとしてサービスへ渡ること")
  @WithMockUser(authorities = "PERM_STORE_STAFF_MANAGE")
  void listPassesStoreIdFilterToService() throws Exception {
    ArgumentCaptor<Long> storeIdCaptor = ArgumentCaptor.forClass(Long.class);
    Page<PlatformStaffResponse> empty = new PageImpl<>(List.of());
    when(platformStaffService.list(any(), storeIdCaptor.capture(), any(), any())).thenReturn(empty);

    mockMvc.perform(get("/platform/staff?storeId=7").principal(ACTOR)).andExpect(status().isOk());

    assertThat(storeIdCaptor.getValue()).isEqualTo(7L);
  }

  @Test
  @DisplayName("storeId 未指定なら絞り込みなし（null）でサービスへ渡ること")
  @WithMockUser(authorities = "PERM_STORE_STAFF_MANAGE")
  void listPassesNullStoreIdWhenParamAbsent() throws Exception {
    ArgumentCaptor<Long> storeIdCaptor = ArgumentCaptor.forClass(Long.class);
    Page<PlatformStaffResponse> empty = new PageImpl<>(List.of());
    when(platformStaffService.list(any(), storeIdCaptor.capture(), any(), any())).thenReturn(empty);

    mockMvc.perform(get("/platform/staff").principal(ACTOR)).andExpect(status().isOk());

    assertThat(storeIdCaptor.getValue()).isNull();
  }

  @Test
  @DisplayName(
      "STAFF_ACCOUNT_MANAGE のみ保持では GET /platform/staff が 403（授権の閲覧・編集は STORE_STAFF_MANAGE 門）")
  @WithMockUser(authorities = "PERM_STAFF_ACCOUNT_MANAGE")
  void accountManageAloneCannotReadGrants() throws Exception {
    mockMvc.perform(get("/platform/staff").principal(ACTOR)).andExpect(status().isForbidden());
  }
}
