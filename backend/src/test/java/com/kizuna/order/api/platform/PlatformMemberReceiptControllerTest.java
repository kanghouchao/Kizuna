package com.kizuna.order.api.platform;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.order.api.dto.MemberReceiptClaimResponse;
import com.kizuna.order.application.MemberReceiptClaimService;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.store.application.StoreActivationService;
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

/**
 * 伝票トークンの申領が会員本人にだけ開いていることの単体テスト。
 *
 * <p>帰属先は認証主体に固定されるため、この経路が会員以外へ開くと、伝票を拾った店舗スタッフが他人の来店を 自分の記録として起こせてしまう。
 */
@WebMvcTest(PlatformMemberReceiptController.class)
@Import({PlatformMemberReceiptControllerTest.MethodSecurityConfig.class, StoreContext.class})
class PlatformMemberReceiptControllerTest {

  /** テスト用にメソッドセキュリティ（@PreAuthorize）を有効化する設定 */
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  private static final String PATH = "/platform/me/receipts";

  private static final String BODY =
      """
      {"token":"raw-token"}
      """;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private MemberReceiptClaimService memberReceiptClaimService;

  // MaintenanceModeInterceptor / StoreExistenceInterceptor は HandlerInterceptor として
  // @WebMvcTest に自動で取り込まれるため、その依存もモックで満たす必要がある。
  @MockitoBean private SystemConfigService systemConfigService;
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean private StoreActivationService storeActivationService;

  @Test
  @DisplayName("会員は伝票を申領でき、帰属記録の生成として 201 が返ること")
  @WithMockUser(authorities = "ROLE_MEMBER")
  void memberCanClaimAReceipt() throws Exception {
    when(memberReceiptClaimService.claim(anyString(), anyString()))
        .thenReturn(new MemberReceiptClaimResponse(120));

    mockMvc.perform(memberPost()).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("トークンの無い要求は要求誤りとして撥ねること")
  @WithMockUser(authorities = "ROLE_MEMBER")
  void blankTokenIsRejected() throws Exception {
    mockMvc
        .perform(
            post(PATH)
                .principal(() -> "member@kizuna.test")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\" \"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("受注管理権限を持つスタッフでも申領には到達できないこと")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void staffIsRejected() throws Exception {
    mockMvc
        .perform(
            post(PATH)
                .principal(() -> "staff@kizuna.test")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isForbidden());
  }

  // 本番は未認証の拒否を PlatformAuthenticationEntryPoint が 401 に変換するが、@WebMvcTest の
  // 最小チェーンにはその entry point が載らないため、ここで見えるのは認可拒否そのもの（403）である。
  @Test
  @DisplayName("匿名では申領に到達できないこと")
  @WithAnonymousUser
  void anonymousIsRejected() throws Exception {
    mockMvc
        .perform(post(PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isForbidden());
  }

  // 本番では認証済み主体をサーブレットコンテナが載せるが、@WebMvcTest の最小チェーンでは載らないため明示する。
  private MockHttpServletRequestBuilder memberPost() {
    return post(PATH)
        .principal(() -> "member@kizuna.test")
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(BODY);
  }
}
