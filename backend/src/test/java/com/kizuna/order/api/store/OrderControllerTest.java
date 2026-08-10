package com.kizuna.order.api.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.order.api.dto.OrderCompletionPreviewResponse;
import com.kizuna.order.api.dto.OrderResponse;
import com.kizuna.order.application.OrderService;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.web.CursorPage;
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
import org.springframework.http.MediaType;
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
  @DisplayName("予約受付 inbox が要求された続きの位置と件数を読み口へ渡すこと")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void reservationRequestsForwardTheRequestedCursor() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    ArgumentCaptor<String> cursorCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Integer> sizeCaptor = ArgumentCaptor.forClass(Integer.class);
    when(orderService.listPendingReservationRequests(cursorCaptor.capture(), sizeCaptor.capture()))
        .thenReturn(new CursorPage<>(List.of(), null));

    mockMvc
        .perform(
            get("/store/orders/reservation-requests?cursor=abc&size=5")
                .header("X-Role", "store")
                .header("X-Store-ID", "1"))
        .andExpect(status().isOk());

    assertThat(cursorCaptor.getValue()).isEqualTo("abc");
    assertThat(sizeCaptor.getValue()).isEqualTo(5);
  }

  @Test
  @DisplayName("続きの位置を指定しない予約受付 inbox は先頭から読むこと")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void reservationRequestsStartFromTheBeginningWithoutACursor() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    ArgumentCaptor<String> cursorCaptor = ArgumentCaptor.forClass(String.class);
    when(orderService.listPendingReservationRequests(cursorCaptor.capture(), anyInt()))
        .thenReturn(new CursorPage<>(List.of(), null));

    mockMvc
        .perform(
            get("/store/orders/reservation-requests")
                .header("X-Role", "store")
                .header("X-Store-ID", "1"))
        .andExpect(status().isOk());

    assertThat(cursorCaptor.getValue()).isNull();
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

  @Test
  @DisplayName("予約申請の編集はキャスト・受付担当を省いても受け付けられること")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void reservationRequestUpdateAcceptsAnOmittedCastAndReceptionist() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(orderService.updateReservationRequest(any(), any()))
        .thenReturn(OrderResponse.builder().build());

    mockMvc
        .perform(storePut("/store/orders/reservation-requests/o1", "{\"pax\": 3}"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("受注管理権限が無ければ予約申請を編集できないこと")
  @WithMockUser(authorities = "PERM_CUSTOMER_MANAGE")
  void reservationRequestUpdateIsRejectedWithoutOrderManage() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(storePut("/store/orders/reservation-requests/o1", "{\"pax\": 3}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("受注管理権限があれば完了処理と事前計算を呼べること")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void completionAndPreviewAreAllowedForOrderManage() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(orderService.complete(any(), any(), any())).thenReturn(OrderResponse.builder().build());
    when(orderService.completionPreview(any(), anyInt()))
        .thenReturn(OrderCompletionPreviewResponse.builder().build());

    mockMvc
        .perform(storePost("/store/orders/o1/completion", "{\"total_fee\": 12000}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(storeGet("/store/orders/o1/completion-preview?total_fee=12000"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("受注管理権限が無ければ完了処理と事前計算が拒否されること")
  @WithMockUser(authorities = "PERM_CUSTOMER_MANAGE")
  void completionAndPreviewAreRejectedWithoutOrderManage() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(storePost("/store/orders/o1/completion", "{\"total_fee\": 12000}"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(storeGet("/store/orders/o1/completion-preview?total_fee=12000"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("会計金額を伴わない完了要求は契約で撥ねられること")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void completionRequiresTheTotalFee() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    // 未指定を 0 として黙って通すと、付与なしの完了が事故として成立する
    mockMvc
        .perform(storePost("/store/orders/o1/completion", "{\"use_points\": 100}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("汎用更新の契約はキャスト・受付担当の省略を受け付けること（可否は受注の状態が決める）")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void orderUpdateContractAcceptsAnOmittedCastAndReceptionist() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(orderService.update(any(), any())).thenReturn(OrderResponse.builder().build());

    // 省略を契約で撥ねると、指名・受付担当が未設定のまま確定した受注が編集できなくなる。
    // 「既にある指名・受付担当は外せない」判定は受注の状態を見るサービス層が持つ（OrderServiceTest）。
    // 店舗起点の受注に対する 400 が経路として維持されることは MemberOrderIT が通しで固定する。
    mockMvc.perform(storePut("/store/orders/o1", "{\"pax\": 3}")).andExpect(status().isOk());
    mockMvc
        .perform(storePut("/store/orders/o1", "{\"cast_id\": \"cast-1\", \"pax\": 3}"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("汎用更新の契約でも人数の下限は撥ねられること")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void orderUpdateStillRejectsAnInvalidPax() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(storePut("/store/orders/o1", "{\"pax\": 0}"))
        .andExpect(status().isBadRequest());
  }

  // 本番では認証済み主体をサーブレットコンテナが載せるが、@WebMvcTest の最小チェーンでは載らないため明示する。
  private MockHttpServletRequestBuilder storePost(String path) {
    return post(path)
        .header("X-Role", "store")
        .header("X-Store-ID", "1")
        .principal(() -> "staff@kizuna.test")
        .with(csrf());
  }

  private MockHttpServletRequestBuilder storePost(String path, String body) {
    return storePost(path).contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private MockHttpServletRequestBuilder storeGet(String path) {
    return get(path)
        .header("X-Role", "store")
        .header("X-Store-ID", "1")
        .principal(() -> "staff@kizuna.test");
  }

  private MockHttpServletRequestBuilder storePut(String path, String body) {
    return put(path)
        .header("X-Role", "store")
        .header("X-Store-ID", "1")
        .principal(() -> "staff@kizuna.test")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
        .with(csrf());
  }
}
