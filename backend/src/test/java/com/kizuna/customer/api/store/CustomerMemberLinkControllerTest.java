package com.kizuna.customer.api.store;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.customer.api.dto.CustomerMemberLinkResponse;
import com.kizuna.customer.application.CustomerMemberLinkService;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.exception.CommonExceptionHandler;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.store.application.StoreActivationService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 会員紐づけ端点の授権と契約の単体テスト。
 *
 * <p>現況の読み口は「紐づいていない」を本体で表さず 404 で返す。呼出側が状態の有無だけで操作の可否を決められる形を、 200 と 404 の両側で固定する。
 */
@WebMvcTest(CustomerMemberLinkController.class)
@Import({
  CustomerMemberLinkControllerTest.MethodSecurityConfig.class,
  StoreContext.class,
  CommonExceptionHandler.class
})
class CustomerMemberLinkControllerTest {

  /** テスト用にメソッドセキュリティ（@PreAuthorize）を有効化する設定 */
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  @Autowired private MockMvc mockMvc;

  @MockitoBean private CustomerMemberLinkService customerMemberLinkService;

  // MaintenanceModeInterceptor / StoreExistenceInterceptor は HandlerInterceptor として
  // @WebMvcTest に自動で取り込まれるため、その依存もモックで満たす必要がある。
  @MockitoBean private SystemConfigService systemConfigService;
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean private StoreActivationService storeActivationService;

  @Test
  @DisplayName("現況の照会は紐づけ済みなら会員コードを返すこと")
  @WithMockUser(authorities = "PERM_CUSTOMER_MANAGE")
  void currentReturnsTheMemberCodeWhenLinked() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(customerMemberLinkService.current(anyString()))
        .thenReturn(
            new CustomerMemberLinkResponse(
                true, "123456789012", OffsetDateTime.parse("2026-08-01T10:00:00+09:00")));

    mockMvc
        .perform(storeGet("/store/customers/c1/member-link"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.member_code").value("123456789012"))
        .andExpect(jsonPath("$.linked").value(true));
  }

  @Test
  @DisplayName("未紐づけの顧客の現況は 404 で返ること")
  @WithMockUser(authorities = "PERM_CUSTOMER_MANAGE")
  void currentIsNotFoundWhenTheCustomerHasNoLink() throws Exception {
    // 「紐づいていない」を 200 の本体で表すと、呼出側が本体を読んでからもう一度分岐することになる
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(customerMemberLinkService.current(anyString()))
        .thenThrow(new NotFoundException("紐づけられている会員がいません"));

    mockMvc
        .perform(storeGet("/store/customers/c1/member-link"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.member_code").doesNotExist());
  }

  @Test
  @DisplayName("現況の照会は CUSTOMER_MANAGE を持たなければ拒否されること")
  @WithMockUser(authorities = "PERM_POINT_ADJUST")
  void currentIsRefusedWithoutCustomerManage() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc.perform(storeGet("/store/customers/c1/member-link")).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("履歴は続きの位置と件数をそのまま読み口へ渡すこと")
  @WithMockUser(authorities = "PERM_CUSTOMER_MANAGE")
  void historyForwardsTheCursorAndSize() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(customerMemberLinkService.history(anyString(), any(), anyInt()))
        .thenReturn(new CursorPage<>(List.of(), null));

    mockMvc
        .perform(storeGet("/store/customers/c1/member-link/history?cursor=abc&size=5"))
        .andExpect(status().isOk());

    verify(customerMemberLinkService).history("c1", "abc", 5);
  }

  private MockHttpServletRequestBuilder storeGet(String path) {
    return get(path)
        .header("X-Role", "store")
        .header("X-Store-ID", "1")
        .principal(() -> "tanaka.hanako@kizuna.test");
  }
}
