package com.kizuna.order.api.me;

import static com.kizuna.shared.web.JwtRequests.jwtUser;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.order.api.dto.OrderMapperImpl;
import com.kizuna.order.application.MemberReceiptClaimResult;
import com.kizuna.order.application.MemberReceiptClaimService;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.user.application.StoreScopeResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 伝票トークンの申領が会員本人にだけ開いていることの単体テスト。
 *
 * <p>帰属先は認証主体に固定されるため、この経路が会員以外へ開くと、伝票を拾った店舗スタッフが他人の来店を 自分の記録として起こせてしまう。
 */
@WebMvcTest(MemberReceiptController.class)
@Import({
  MemberReceiptControllerTest.MethodSecurityConfig.class,
  OrderMapperImpl.class,
  StoreScopeHolder.class
})
class MemberReceiptControllerTest {

  private static final long USER_ID = 7L;

  /** テスト用にメソッドセキュリティ（@PreAuthorize）を有効化する設定 */
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  private static final String PATH = "/me/receipts";

  private static final String BODY =
      """
      {"token":"raw-token"}
      """;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private MemberReceiptClaimService memberReceiptClaimService;

  // CurrentStoreArgumentResolver（引数解決器）は @WebMvcTest に自動で取り込まれるため、
  // その依存もモックで満たす必要がある。
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean StoreScopeResolver storeScopeResolver;

  @Test
  @DisplayName("会員は伝票を申領でき、帰属記録の生成として 201 が返ること")
  void memberCanClaimAReceipt() throws Exception {
    when(memberReceiptClaimService.claim(anyLong(), anyString()))
        .thenReturn(new MemberReceiptClaimResult(120));

    mockMvc
        .perform(memberPost().with(jwtUser(USER_ID, "ROLE_MEMBER")))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("トークンの無い要求は要求誤りとして撥ねること")
  void blankTokenIsRejected() throws Exception {
    mockMvc
        .perform(
            post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\" \"}")
                .with(jwtUser(USER_ID, "ROLE_MEMBER")))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("受注管理権限を持つスタッフでも申領には到達できないこと")
  void staffIsRejected() throws Exception {
    mockMvc
        .perform(
            post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY)
                .with(jwtUser(USER_ID, "PERM_ORDER_MANAGE")))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("匿名では申領に到達できないこと")
  @WithAnonymousUser
  void anonymousIsRejected() throws Exception {
    mockMvc
        .perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isUnauthorized());
  }

  private MockHttpServletRequestBuilder memberPost() {
    return post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY);
  }
}
