package com.kizuna.order.api.platform;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.order.api.dto.MemberOrderResponse;
import com.kizuna.order.application.MemberOrderService;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.store.application.StoreActivationService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 会員本人の予約経路が会員にだけ開いていることの単体テスト。 */
@WebMvcTest(PlatformMemberOrderController.class)
@Import({PlatformMemberOrderControllerTest.MethodSecurityConfig.class, StoreContext.class})
class PlatformMemberOrderControllerTest {

  /** テスト用にメソッドセキュリティ（@PreAuthorize）を有効化する設定 */
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  private static final String BODY =
      """
      {"store_id":1,"business_date":"2999-01-01","pax":2}
      """;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private MemberOrderService memberOrderService;

  // MaintenanceModeInterceptor / StoreExistenceInterceptor は HandlerInterceptor として
  // @WebMvcTest に自動で取り込まれるため、その依存もモックで満たす必要がある。
  @MockitoBean private SystemConfigService systemConfigService;
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean private StoreActivationService storeActivationService;

  @Test
  @DisplayName("会員は予約の申請・一覧・取り下げができること")
  @WithMockUser(authorities = "ROLE_MEMBER")
  void memberCanUseOwnReservationRoutes() throws Exception {
    when(memberOrderService.request(anyString(), any()))
        .thenReturn(MemberOrderResponse.builder().build());
    when(memberOrderService.list(anyString(), any(), anyInt()))
        .thenReturn(new CursorPage<>(List.of(), null));
    when(memberOrderService.cancel(anyString(), anyString()))
        .thenReturn(MemberOrderResponse.builder().build());

    mockMvc
        .perform(
            memberPost("/platform/me/orders").contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isCreated());
    mockMvc
        .perform(get("/platform/me/orders").principal(() -> "member@kizuna.test"))
        .andExpect(status().isOk());
    mockMvc.perform(memberPost("/platform/me/orders/o1/cancellation")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("キャストは会員の予約経路に到達できないこと")
  @WithMockUser(authorities = "ROLE_CAST")
  void castIsRejected() throws Exception {
    mockMvc
        .perform(get("/platform/me/orders").principal(() -> "cast@kizuna.test"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("受注管理権限を持つスタッフでも会員の予約経路に到達できないこと")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void staffIsRejected() throws Exception {
    mockMvc
        .perform(get("/platform/me/orders").principal(() -> "staff@kizuna.test"))
        .andExpect(status().isForbidden());
  }

  // 本番は未認証の拒否を PlatformAuthenticationEntryPoint が 401 に変換するが、@WebMvcTest の
  // 最小チェーンにはその entry point が載らないため、ここで見えるのは認可拒否そのもの（403）である。
  @Test
  @DisplayName("匿名では会員の予約経路に到達できないこと")
  @WithAnonymousUser
  void anonymousIsRejected() throws Exception {
    mockMvc.perform(get("/platform/me/orders")).andExpect(status().isForbidden());
  }

  // 本番では認証済み主体をサーブレットコンテナが載せるが、@WebMvcTest の最小チェーンでは載らないため明示する。
  private MockHttpServletRequestBuilder memberPost(String path) {
    return post(path).principal(() -> "member@kizuna.test").with(csrf());
  }
}
