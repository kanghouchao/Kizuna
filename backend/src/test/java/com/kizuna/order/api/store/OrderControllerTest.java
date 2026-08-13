package com.kizuna.order.api.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.order.api.dto.OrderAttributionResponse;
import com.kizuna.order.api.dto.OrderCompletionPreviewResponse;
import com.kizuna.order.api.dto.OrderResponse;
import com.kizuna.order.application.OrderAttributionCorrectionService;
import com.kizuna.order.application.OrderAttributionService;
import com.kizuna.order.application.OrderService;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.store.application.StoreActivationService;
import java.sql.SQLException;
import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
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
  @MockitoBean private OrderAttributionService orderAttributionService;
  @MockitoBean private OrderAttributionCorrectionService orderAttributionCorrectionService;

  // MaintenanceModeInterceptor / StoreExistenceInterceptor は HandlerInterceptor として
  // @WebMvcTest に自動で取り込まれるため、その依存もモックで満たす必要がある。
  @MockitoBean private SystemConfigService systemConfigService;
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean private StoreActivationService storeActivationService;

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
  @DisplayName("列に収まらない連絡先は契約で撥ねられること")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void orderCreateRejectsContactLongerThanItsColumn() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    // 顧客が着かない受注では録入された連絡先が t_orders へ入る。契約で撥ねないと、溢れた値は
    // 挿入時の SQLSTATE 22001 になり、理由の分かる 400 ではなく 500 で返る
    String body =
        "{\"receptionist_id\": 1, \"business_date\": \"2026-08-12\", \"cast_id\": \"cast-1\""
            + ", \"customer_name\": \""
            + "あ".repeat(256)
            + "\"}";

    mockMvc.perform(storePost("/store/orders", body)).andExpect(status().isBadRequest());
    verifyNoInteractions(orderService);
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

  @Test
  @DisplayName("関連の並行成立に敗れた確定は取り直され、成功応答になること")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void confirmationLosingTheLinkRaceIsRetried() throws Exception {
    // 敗者の収束をここで固定する。統合テストの並行確定は「2 つの確定が実際に競った」ことを
    // 保証できない（勝者が先に commit すれば敗者は素直に再利用の枝へ落ちる）ため、取り直しの
    // 配線そのものは決定的なこの単体テストが受け持つ。
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(orderService.confirm(any(), any()))
        .thenThrow(integrityViolation("uq_t_customer_member_links_active_member"))
        .thenReturn(OrderResponse.builder().id("o1").build());

    mockMvc.perform(storePost("/store/orders/o1/confirmation")).andExpect(status().isOk());

    verify(orderService, times(2)).confirm(any(), any());
  }

  @Test
  @DisplayName("関連以外の整合性違反では確定を取り直さないこと")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void confirmationDoesNotRetryOnOtherIntegrityViolations() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(orderService.confirm(any(), any())).thenThrow(integrityViolation("uq_t_users_email"));

    // 写像を持たない違反は実装欠陥として全域ハンドラの分類（一意違反 = 409）へ落とす
    mockMvc.perform(storePost("/store/orders/o1/confirmation")).andExpect(status().isConflict());

    verify(orderService, times(1)).confirm(any(), any());
  }

  @Test
  @DisplayName("受注管理も店長権限も無ければ帰属の閲覧・無効化・伝票の再発行がいずれも拒否されること")
  @WithMockUser(authorities = "PERM_CUSTOMER_MANAGE")
  void attributionCorrectionIsRejectedWithoutOrderManage() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc.perform(storeGet("/store/orders/o1/attribution")).andExpect(status().isForbidden());
    mockMvc
        .perform(
            storePost(
                "/store/orders/o1/attribution/invalidation",
                "{\"attribution_id\": 1, \"reason\": \"取り違え\"}"))
        .andExpect(status().isForbidden());
    mockMvc.perform(storePost("/store/orders/o1/receipt-token")).andExpect(status().isForbidden());
    verifyNoInteractions(orderAttributionService);
  }

  @Test
  @DisplayName("受注管理だけの店員は帰属を無効化できず、台帳の訂正にも届かないこと")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void invalidationAndCorrectionRequirePointAdjust() throws Exception {
    // 不可逆で準金銭的な訂正の一段目だけを店員に許すと、二段目の台帳訂正を実行できない者が訂正を始められ、
    // やり残しが人を跨いで残る（ADR 0012）。要求そのものは妥当な形なので、撥ねているのは権限だけである。
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(
            storePost(
                "/store/orders/o1/attribution/invalidation",
                "{\"attribution_id\": 1, \"reason\": \"取り違え\"}"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            storePost(
                "/store/orders/o1/attribution/correction",
                "{\"attribution_id\": 1, \"points\": 100, \"reason\": \"取り違え\","
                    + " \"idempotency_key\": \"k1\"}"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(storeGet("/store/orders/o1/attribution/correction?attribution_id=1"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(orderAttributionService);
    verifyNoInteractions(orderAttributionCorrectionService);
  }

  @Test
  @DisplayName("理由の無い無効化は撥ねられ、サービスへ届かないこと")
  @WithMockUser(authorities = "PERM_POINT_ADJUST")
  void invalidationWithoutAReasonIsRejected() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(
            storePost(
                "/store/orders/o1/attribution/invalidation",
                "{\"attribution_id\": 1, \"reason\": \" \"}"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(storePost("/store/orders/o1/attribution/invalidation", "{}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(orderAttributionService);
  }

  @Test
  @DisplayName("対象の帰属記録を名指さない無効化は撥ねられ、サービスへ届かないこと")
  @WithMockUser(authorities = "PERM_POINT_ADJUST")
  void invalidationWithoutATargetIsRejected() throws Exception {
    // 受注から対象を導く形へ戻ると、画面が見ていた記録と別の記録を消しうる
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(storePost("/store/orders/o1/attribution/invalidation", "{\"reason\": \"取り違え\"}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(orderAttributionService);
  }

  @Test
  @DisplayName("列長を超える理由は 400 で撥ねられること（DB のエラーにしない）")
  @WithMockUser(authorities = "PERM_POINT_ADJUST")
  void invalidationWithAnOverlongReasonIsRejected() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    String body = "{\"attribution_id\": 1, \"reason\": \"" + "あ".repeat(501) + "\"}";

    mockMvc
        .perform(storePost("/store/orders/o1/attribution/invalidation", body))
        .andExpect(status().isBadRequest());

    // 正向対照: 上限ちょうどは通る
    when(orderAttributionService.invalidate(any(), any(), any()))
        .thenReturn(new OrderAttributionResponse(null, false, null, null, null, null, null));
    mockMvc
        .perform(
            storePost(
                "/store/orders/o1/attribution/invalidation",
                "{\"attribution_id\": 1, \"reason\": \"" + "あ".repeat(500) + "\"}"))
        .andExpect(status().isOk());
  }

  /** 制約名を持つ整合性違反。全域ハンドラの一意違反判定（SQLSTATE 23505）も通る形にする。 */
  private static DataIntegrityViolationException integrityViolation(String constraintName) {
    return new DataIntegrityViolationException(
        "duplicate",
        new ConstraintViolationException(
            "could not execute statement",
            new SQLException("duplicate key value", "23505"),
            constraintName));
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
