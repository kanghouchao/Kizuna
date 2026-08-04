package com.kizuna.order.api.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.order.api.dto.OrderResponse;
import com.kizuna.order.application.OrderService;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreExistenceCheck;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 呼出側の {@code ?sort=} 上書きで一意な副キー id が消えないことの単体テスト（offset ページングの安定性）。 */
@WebMvcTest(OrderController.class)
@Import({OrderControllerTest.MethodSecurityConfig.class, StoreContext.class})
class OrderControllerTest {

  /** テスト用にメソッドセキュリティ（@PreAuthorize）を有効化する設定 */
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  @Autowired private MockMvc mockMvc;

  @MockitoBean private OrderService orderService;

  // MaintenanceModeInterceptor / StoreExistenceInterceptor は HandlerInterceptor として
  // @WebMvcTest に自動で取り込まれるため、その依存もモックで満たす必要がある。
  @MockitoBean private SystemConfigService systemConfigService;
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;

  @Test
  @DisplayName("GET /store/orders?sort=businessDate でも id が副キーとして補われること")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void listAppendsIdTiebreakerWhenCallerOverridesSort() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    Page<OrderResponse> empty = new PageImpl<>(List.of());
    when(orderService.list(any(), pageableCaptor.capture())).thenReturn(empty);

    mockMvc
        .perform(
            get("/store/orders?sort=businessDate")
                .header("X-Role", "store")
                .header("X-Store-ID", "1"))
        .andExpect(status().isOk());

    assertThat(pageableCaptor.getValue().getSort().getOrderFor("id")).isNotNull();
  }

  @Test
  @DisplayName("予約受付 inbox が要求されたページを読み口へ渡すこと")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void reservationRequestsForwardTheRequestedPage() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    when(orderService.listPendingReservationRequests(pageableCaptor.capture()))
        .thenReturn(new PageImpl<>(List.of()));

    mockMvc
        .perform(
            get("/store/orders/reservation-requests?page=1&size=5")
                .header("X-Role", "store")
                .header("X-Store-ID", "1"))
        .andExpect(status().isOk());

    assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
    assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
  }

  @Test
  @DisplayName("受注管理権限が無ければ予約受付 inbox を読めないこと")
  @WithMockUser(authorities = "PERM_CUSTOMER_MANAGE")
  void reservationRequestsAreRejectedWithoutOrderManage() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(
            get("/store/orders/reservation-requests")
                .header("X-Role", "store")
                .header("X-Store-ID", "1"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("受注管理権限があれば予約申請を確定・謝絶できること")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void confirmAndDeclineAreAllowedForOrderManage() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(orderService.confirm(any(), any())).thenReturn(OrderResponse.builder().build());
    when(orderService.decline(any())).thenReturn(OrderResponse.builder().build());

    mockMvc.perform(storePost("/store/orders/o1/confirmation")).andExpect(status().isOk());
    mockMvc.perform(storePost("/store/orders/o1/decline")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("受注管理権限が無ければ予約申請の確定・謝絶が拒否されること")
  @WithMockUser(authorities = "PERM_CUSTOMER_MANAGE")
  void confirmAndDeclineAreRejectedWithoutOrderManage() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc.perform(storePost("/store/orders/o1/confirmation")).andExpect(status().isForbidden());
    mockMvc.perform(storePost("/store/orders/o1/decline")).andExpect(status().isForbidden());
  }

  // 本番では認証済み主体をサーブレットコンテナが載せるが、@WebMvcTest の最小チェーンでは載らないため明示する。
  private MockHttpServletRequestBuilder storePost(String path) {
    return post(path)
        .header("X-Role", "store")
        .header("X-Store-ID", "1")
        .principal(() -> "staff@kizuna.test")
        .with(csrf());
  }
}
