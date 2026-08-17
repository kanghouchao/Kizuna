package com.kizuna.customer.api.store;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.customer.api.dto.CustomerMergeHistoryResponse;
import com.kizuna.customer.api.dto.MergeDirection;
import com.kizuna.customer.application.CustomerMergeService;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.exception.CommonExceptionHandler;
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
 * 統合履歴の読み口の授権と契約の単体テスト。
 *
 * <p>実行（{@code POST}）と読み（{@code GET}）が同じパスに載るので、権限の要求が両方に掛かっていることと、 続きの位置・件数が素通しで渡ることを固定する。
 */
@WebMvcTest(CustomerMergeController.class)
@Import({
  CustomerMergeControllerTest.MethodSecurityConfig.class,
  StoreContext.class,
  CommonExceptionHandler.class
})
class CustomerMergeControllerTest {

  /** テスト用にメソッドセキュリティ（@PreAuthorize）を有効化する設定 */
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  @Autowired private MockMvc mockMvc;

  @MockitoBean private CustomerMergeService customerMergeService;

  // MaintenanceModeInterceptor / StoreExistenceInterceptor は HandlerInterceptor として
  // @WebMvcTest に自動で取り込まれるため、その依存もモックで満たす必要がある。
  @MockitoBean private SystemConfigService systemConfigService;
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean private StoreActivationService storeActivationService;

  @Test
  @DisplayName("履歴は向き・相手の行・実行者・移した件数を snake_case で返すこと")
  @WithMockUser(authorities = "PERM_CUSTOMER_MERGE")
  void historyExposesTheEvidenceInSnakeCase() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(customerMergeService.history(anyString(), any(), anyInt()))
        .thenReturn(
            new CursorPage<>(
                List.of(
                    new CustomerMergeHistoryResponse(
                        "m1",
                        MergeDirection.SURVIVING,
                        "c2",
                        "山田太郎",
                        "田中花子",
                        OffsetDateTime.parse("2026-08-10T10:00:00+09:00"),
                        3,
                        1)),
                null));

    mockMvc
        .perform(storeGet("/store/customers/c1/merges"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].direction").value("SURVIVING"))
        .andExpect(jsonPath("$.content[0].counterpart_customer_id").value("c2"))
        .andExpect(jsonPath("$.content[0].counterpart_customer_name").value("山田太郎"))
        .andExpect(jsonPath("$.content[0].merged_by_name").value("田中花子"))
        .andExpect(jsonPath("$.content[0].moved_order_count").value(3))
        .andExpect(jsonPath("$.content[0].moved_link_count").value(1))
        // 続きが無いときは non_null 直列化で欄ごと現れない
        .andExpect(jsonPath("$.next_cursor").doesNotExist());
  }

  @Test
  @DisplayName("続きの位置と件数がそのまま渡ること")
  @WithMockUser(authorities = "PERM_CUSTOMER_MERGE")
  void historyForwardsTheCursorAndSize() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(customerMergeService.history(anyString(), any(), anyInt()))
        .thenReturn(new CursorPage<>(List.of(), null));

    mockMvc
        .perform(storeGet("/store/customers/c1/merges?cursor=abc&size=5"))
        .andExpect(status().isOk());

    verify(customerMergeService).history("c1", "abc", 5);
  }

  @Test
  @DisplayName("統合権限を持たない利用者は履歴を読めず、読み口にも届かないこと")
  @WithMockUser(authorities = "PERM_CUSTOMER_MANAGE")
  void historyIsForbiddenWithoutTheMergePermission() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc.perform(storeGet("/store/customers/c1/merges")).andExpect(status().isForbidden());

    // 履歴は誰がどの顧客を畳んだかを明かす。拒否は読み取りの手前で成立していること
    verify(customerMergeService, never()).history(anyString(), any(), anyInt());
  }

  private MockHttpServletRequestBuilder storeGet(String path) {
    return get(path)
        .header("X-Role", "store")
        .header("X-Store-ID", "1")
        .principal(() -> "tanaka.hanako@kizuna.test");
  }
}
