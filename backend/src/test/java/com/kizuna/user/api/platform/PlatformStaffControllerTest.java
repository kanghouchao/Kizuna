package com.kizuna.user.api.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.user.api.dto.PlatformStaffResponse;
import com.kizuna.user.application.PlatformStaffService;
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
@Import({PlatformStaffControllerTest.MethodSecurityConfig.class, StoreContext.class})
class PlatformStaffControllerTest {

  /** テスト用にメソッドセキュリティ（@PreAuthorize）を有効化する設定 */
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PlatformStaffService platformStaffService;

  // MaintenanceModeInterceptor / StoreExistenceInterceptor は HandlerInterceptor として
  // @WebMvcTest に自動で取り込まれるため、その依存もモックで満たす必要がある。
  @MockitoBean private SystemConfigService systemConfigService;
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;

  @Test
  @DisplayName("GET /platform/staff?sort=displayName でも id が副キーとして補われること")
  @WithMockUser(authorities = "PERM_STAFF_MANAGE")
  void listAppendsIdTiebreakerWhenCallerOverridesSort() throws Exception {
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    Page<PlatformStaffResponse> empty = new PageImpl<>(List.of());
    when(platformStaffService.list(any(), any(), pageableCaptor.capture())).thenReturn(empty);

    mockMvc.perform(get("/platform/staff?sort=displayName")).andExpect(status().isOk());

    assertThat(pageableCaptor.getValue().getSort().getOrderFor("id")).isNotNull();
  }

  @Test
  @DisplayName("GET /platform/staff?storeId= が店舗絞り込みとしてサービスへ渡ること")
  @WithMockUser(authorities = "PERM_STAFF_MANAGE")
  void listPassesStoreIdFilterToService() throws Exception {
    ArgumentCaptor<Long> storeIdCaptor = ArgumentCaptor.forClass(Long.class);
    Page<PlatformStaffResponse> empty = new PageImpl<>(List.of());
    when(platformStaffService.list(any(), storeIdCaptor.capture(), any())).thenReturn(empty);

    mockMvc.perform(get("/platform/staff?storeId=7")).andExpect(status().isOk());

    assertThat(storeIdCaptor.getValue()).isEqualTo(7L);
  }

  @Test
  @DisplayName("storeId 未指定なら絞り込みなし（null）でサービスへ渡ること")
  @WithMockUser(authorities = "PERM_STAFF_MANAGE")
  void listPassesNullStoreIdWhenParamAbsent() throws Exception {
    ArgumentCaptor<Long> storeIdCaptor = ArgumentCaptor.forClass(Long.class);
    Page<PlatformStaffResponse> empty = new PageImpl<>(List.of());
    when(platformStaffService.list(any(), storeIdCaptor.capture(), any())).thenReturn(empty);

    mockMvc.perform(get("/platform/staff")).andExpect(status().isOk());

    assertThat(storeIdCaptor.getValue()).isNull();
  }
}
