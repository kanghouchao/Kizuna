package com.kizuna.customer.api.store;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.customer.api.dto.CustomerPointBalanceResponse;
import com.kizuna.customer.application.CustomerPointService;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * ポイント端点の授権と契約の単体テスト。
 *
 * <p>残高の照会は顧客台帳の読みとして CUSTOMER_MANAGE で足りるが、手動調整は残高を人手で動かす確定操作なので 別権限で仕切られていることを固定する。
 */
@WebMvcTest(CustomerPointController.class)
@Import({CustomerPointControllerTest.MethodSecurityConfig.class, StoreContext.class})
class CustomerPointControllerTest {

  /** テスト用にメソッドセキュリティ（@PreAuthorize）を有効化する設定 */
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  @Autowired private MockMvc mockMvc;

  @MockitoBean private CustomerPointService customerPointService;

  // MaintenanceModeInterceptor / StoreExistenceInterceptor は HandlerInterceptor として
  // @WebMvcTest に自動で取り込まれるため、その依存もモックで満たす必要がある。
  @MockitoBean private SystemConfigService systemConfigService;
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;

  @Test
  @DisplayName("残高照会は CUSTOMER_MANAGE で到達できること")
  @WithMockUser(authorities = "PERM_CUSTOMER_MANAGE")
  void balanceIsReachableWithCustomerManage() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(customerPointService.balance(anyString()))
        .thenReturn(CustomerPointBalanceResponse.builder().linked(false).build());

    mockMvc
        .perform(storeGet("/store/customers/c1/member-point-balance"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("残高照会は CUSTOMER_MANAGE を持たなければ拒否されること")
  @WithMockUser(authorities = "PERM_POINT_ADJUST")
  void balanceIsRefusedWithoutCustomerManage() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(storeGet("/store/customers/c1/member-point-balance"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("手動調整は CUSTOMER_MANAGE だけでは拒否されること")
  @WithMockUser(authorities = "PERM_CUSTOMER_MANAGE")
  void adjustmentIsRefusedWithoutThePointPermission() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(
            storePost(
                "/store/customers/c1/point-adjustments", "{\"delta\": 100, \"reason\": \"訂正\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("手動調整は POINT_ADJUST を持てば到達できること（403 が端点の不在でない証明）")
  @WithMockUser(authorities = {"PERM_CUSTOMER_MANAGE", "PERM_POINT_ADJUST"})
  void adjustmentIsReachableWithThePointPermission() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(customerPointService.adjust(anyString(), any(), anyString()))
        .thenReturn(CustomerPointBalanceResponse.builder().linked(true).balance(100L).build());

    mockMvc
        .perform(
            storePost(
                "/store/customers/c1/point-adjustments", "{\"delta\": 100, \"reason\": \"訂正\"}"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("理由が空白だけの調整は契約で撥ねられること")
  @WithMockUser(authorities = {"PERM_CUSTOMER_MANAGE", "PERM_POINT_ADJUST"})
  void adjustmentRequiresANonBlankReason() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(
            storePost(
                "/store/customers/c1/point-adjustments", "{\"delta\": 100, \"reason\": \"   \"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("増減の無い調整は契約で撥ねられること")
  @WithMockUser(authorities = {"PERM_CUSTOMER_MANAGE", "PERM_POINT_ADJUST"})
  void adjustmentRequiresTheDelta() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(storePost("/store/customers/c1/point-adjustments", "{\"reason\": \"訂正\"}"))
        .andExpect(status().isBadRequest());
  }

  private MockHttpServletRequestBuilder storeGet(String path) {
    return get(path)
        .header("X-Role", "store")
        .header("X-Store-ID", "1")
        .principal(() -> "tanaka.hanako@kizuna.test");
  }

  private MockHttpServletRequestBuilder storePost(String path, String body) {
    return post(path)
        .header("X-Role", "store")
        .header("X-Store-ID", "1")
        .principal(() -> "tanaka.hanako@kizuna.test")
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }
}
