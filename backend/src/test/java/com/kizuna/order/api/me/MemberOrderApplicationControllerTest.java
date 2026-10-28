package com.kizuna.order.api.me;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.order.api.dto.OrderMapperImpl;
import com.kizuna.order.application.MemberOrderApplicationResult;
import com.kizuna.order.application.MemberOrderApplicationService;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.user.application.StoreScopeResolver;
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

/** 会員本人の予約申請経路が会員にだけ開いていることの単体テスト。 */
@WebMvcTest(MemberOrderApplicationController.class)
@Import({
  MemberOrderApplicationControllerTest.MethodSecurityConfig.class,
  OrderMapperImpl.class,
  StoreScopeHolder.class
})
class MemberOrderApplicationControllerTest {

  /** テスト用にメソッドセキュリティ（@PreAuthorize）を有効化する設定 */
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  private static MemberOrderApplicationResult emptyResult() {
    return new MemberOrderApplicationResult(null, null, null, null, null, null, null, null, false);
  }

  private static final String BODY =
      """
      {"store_id":1,"business_date":"2999-01-01","pax":2,"declared_name":"名乗り太郎"}
      """;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private MemberOrderApplicationService memberOrderApplicationService;

  // CurrentStoreArgumentResolver（引数解決器）は @WebMvcTest に自動で取り込まれるため、
  // その依存もモックで満たす必要がある。
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean StoreScopeResolver storeScopeResolver;

  @Test
  @DisplayName("会員は予約の申請・一覧・取り下げができること")
  @WithMockUser(authorities = "ROLE_MEMBER")
  void memberCanUseOwnApplicationRoutes() throws Exception {
    when(memberOrderApplicationService.request(anyString(), any())).thenReturn(emptyResult());
    when(memberOrderApplicationService.list(anyString(), any(), anyInt()))
        .thenReturn(new CursorPage<>(List.of(), null));
    when(memberOrderApplicationService.withdraw(anyString(), anyString()))
        .thenReturn(emptyResult());

    mockMvc
        .perform(
            memberPost("/me/order-applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isCreated());
    mockMvc
        .perform(get("/me/order-applications").principal(() -> "member@kizuna.test"))
        .andExpect(status().isOk());
    mockMvc.perform(memberPost("/me/order-applications/a1/withdrawal")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("キャストは会員の予約申請経路に到達できないこと")
  @WithMockUser(authorities = "ROLE_CAST")
  void castIsRejected() throws Exception {
    mockMvc
        .perform(get("/me/order-applications").principal(() -> "cast@kizuna.test"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("受注管理権限を持つスタッフでも会員の予約申請経路に到達できないこと")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void staffIsRejected() throws Exception {
    mockMvc
        .perform(get("/me/order-applications").principal(() -> "staff@kizuna.test"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("匿名では会員の予約申請経路に到達できないこと")
  @WithAnonymousUser
  void anonymousIsRejected() throws Exception {
    mockMvc.perform(get("/me/order-applications")).andExpect(status().isUnauthorized());
  }

  // 本番では認証済み主体をサーブレットコンテナが載せるが、@WebMvcTest の最小チェーンでは載らないため明示する。
  private MockHttpServletRequestBuilder memberPost(String path) {
    return post(path).principal(() -> "member@kizuna.test").with(csrf());
  }
}
