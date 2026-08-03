package com.kizuna.shift.api.platform;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shift.application.ConfirmedShiftLookupService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 指名候補の出勤参照が会員本人にだけ開いていることの単体テスト。 */
@WebMvcTest(PlatformShiftController.class)
@Import({PlatformShiftControllerTest.MethodSecurityConfig.class, StoreContext.class})
class PlatformShiftControllerTest {

  /** テスト用にメソッドセキュリティ（@PreAuthorize）を有効化する設定 */
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  private static final String PATH = "/platform/shifts/casts?store_id=1&date=2999-01-01";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ConfirmedShiftLookupService confirmedShiftLookupService;

  // MaintenanceModeInterceptor / StoreExistenceInterceptor は HandlerInterceptor として
  // @WebMvcTest に自動で取り込まれるため、その依存もモックで満たす必要がある。
  @MockitoBean private SystemConfigService systemConfigService;
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;

  @Test
  @DisplayName("会員は指定店舗・指定日の確定シフトを引けること")
  @WithMockUser(authorities = "ROLE_MEMBER")
  void memberCanReadConfirmedCasts() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(confirmedShiftLookupService.listConfirmedCasts(anyLong(), any())).thenReturn(List.of());

    mockMvc.perform(get(PATH)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("キャストは指名候補の出勤参照に到達できないこと")
  @WithMockUser(authorities = "ROLE_CAST")
  void castIsRejected() throws Exception {
    mockMvc.perform(get(PATH)).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("受注管理権限を持つスタッフでも会員向け経路には到達できないこと")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void staffIsRejected() throws Exception {
    mockMvc.perform(get(PATH)).andExpect(status().isForbidden());
  }

  // 本番は未認証の拒否を PlatformAuthenticationEntryPoint が 401 に変換するが、@WebMvcTest の
  // 最小チェーンにはその entry point が載らないため、ここで見えるのは認可拒否そのもの（403）である。
  @Test
  @DisplayName("匿名では指名候補の出勤参照に到達できないこと")
  @WithAnonymousUser
  void anonymousIsRejected() throws Exception {
    mockMvc.perform(get(PATH)).andExpect(status().isForbidden());
  }
}
