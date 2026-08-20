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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.order.api.dto.GuestOrderApplicationCreateRequest;
import com.kizuna.order.api.dto.GuestOrderApplicationResponse;
import com.kizuna.order.api.dto.OrderApplicationResponse;
import com.kizuna.order.api.dto.OrderResponse;
import com.kizuna.order.application.GuestOrderApplicationService;
import com.kizuna.order.application.OrderService;
import com.kizuna.order.domain.OrderApplicationStatus;
import com.kizuna.order.infrastructure.GuestApplicationRateLimiter;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.store.application.StoreActivationService;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 予約受付箱（申請の一覧・確定・謝絶）の授権と契約の単体テスト。 */
@WebMvcTest(OrderApplicationController.class)
@Import({OrderApplicationControllerTest.MethodSecurityConfig.class, StoreContext.class})
class OrderApplicationControllerTest {

  /** テスト用にメソッドセキュリティ（@PreAuthorize）を有効化する設定 */
  @TestConfiguration
  @EnableMethodSecurity
  static class MethodSecurityConfig {}

  private static final String CONFIRMATION_BODY = "{\"business_date\": \"2026-08-20\", \"pax\": 2}";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private OrderService orderService;
  @MockitoBean private GuestOrderApplicationService guestOrderApplicationService;
  @MockitoBean private GuestApplicationRateLimiter guestApplicationRateLimiter;

  // MaintenanceModeInterceptor / StoreExistenceInterceptor は HandlerInterceptor として
  // @WebMvcTest に自動で取り込まれるため、その依存もモックで満たす必要がある。
  @MockitoBean private SystemConfigService systemConfigService;
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean private StoreActivationService storeActivationService;

  @Test
  @DisplayName("受付箱が要求された群・続きの位置を読み口へ渡すこと")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void listForwardsTheRequestedStatusesAndCursor() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    ArgumentCaptor<Set<OrderApplicationStatus>> statusesCaptor = ArgumentCaptor.captor();
    ArgumentCaptor<String> cursorCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Integer> sizeCaptor = ArgumentCaptor.forClass(Integer.class);
    when(orderService.listApplications(
            statusesCaptor.capture(), cursorCaptor.capture(), sizeCaptor.capture()))
        .thenReturn(new CursorPage<>(List.of(), null));

    mockMvc
        .perform(storeGet("/store/order-applications?statuses=PENDING&cursor=abc&size=5"))
        .andExpect(status().isOk());

    assertThat(statusesCaptor.getValue()).containsExactly(OrderApplicationStatus.PENDING);
    assertThat(cursorCaptor.getValue()).isEqualTo("abc");
    assertThat(sizeCaptor.getValue()).isEqualTo(5);
  }

  @Test
  @DisplayName("受注管理権限があれば申請を確定・謝絶でき、確定は生成した受注を 201 で返すこと")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void confirmAndDeclineAreAllowedForOrderManage() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(orderService.confirmApplication(any(), any(), any()))
        .thenReturn(OrderResponse.builder().id("order-1").build());

    mockMvc
        .perform(storePost("/store/order-applications/a1/confirmation", CONFIRMATION_BODY))
        .andExpect(status().isCreated());
    // 謝絶は結果を読まれない操作なので 204（本体なし）で返る。
    mockMvc
        .perform(storePost("/store/order-applications/a1/refusal", "{\"reason\": \"満席\"}"))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("受注管理権限が無ければ受付箱の一覧・確定・謝絶がいずれも拒否されること")
  @WithMockUser(authorities = "PERM_CUSTOMER_MANAGE")
  void everyRouteIsRejectedWithoutOrderManage() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(storeGet("/store/order-applications?statuses=PENDING"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(storePost("/store/order-applications/a1/confirmation", CONFIRMATION_BODY))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(storePost("/store/order-applications/a1/refusal", "{\"reason\": \"満席\"}"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(orderService);
  }

  @Test
  @DisplayName("営業日を伴わない確定は契約で撥ねられること")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void confirmationRequiresTheBusinessDate() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(storePost("/store/order-applications/a1/confirmation", "{\"pax\": 2}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(orderService);
  }

  @Test
  @DisplayName("理由の無い謝絶は契約で撥ねられること")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void refusalRequiresAReason() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    // 空白だけの理由も「書いていない」と同じに扱う
    mockMvc
        .perform(storePost("/store/order-applications/a1/refusal", "{\"reason\": \"   \"}"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(storePost("/store/order-applications/a1/refusal", "{}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(orderService);
  }

  @Test
  @DisplayName("列長を超える謝絶の理由は 400 で撥ねられること（DB のエラーにしない）")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void refusalWithAnOverlongReasonIsRejected() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    String body = "{\"reason\": \"" + "あ".repeat(501) + "\"}";

    mockMvc
        .perform(storePost("/store/order-applications/a1/refusal", body))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(orderService);

    // 正向対照: 上限ちょうどは通る（謝絶は結果を読まれない操作なので 204）
    mockMvc
        .perform(
            storePost(
                "/store/order-applications/a1/refusal",
                "{\"reason\": \"" + "あ".repeat(500) + "\"}"))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("関連の並行成立に敗れた確定は取り直され、成功応答になること")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void confirmationLosingTheLinkRaceIsRetried() throws Exception {
    // 敗者の収束をここで固定する。統合テストの並行確定は「2 つの確定が実際に競った」ことを
    // 保証できない（勝者が先に commit すれば敗者は素直に再利用の枝へ落ちる）ため、取り直しの
    // 配線そのものは決定的なこの単体テストが受け持つ。
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(orderService.confirmApplication(any(), any(), any()))
        .thenThrow(integrityViolation("uq_t_customer_member_links_active_member"))
        .thenReturn(OrderResponse.builder().id("order-1").build());

    mockMvc
        .perform(storePost("/store/order-applications/a1/confirmation", CONFIRMATION_BODY))
        .andExpect(status().isCreated());

    verify(orderService, times(2)).confirmApplication(any(), any(), any());
  }

  @Test
  @DisplayName("関連以外の整合性違反では確定を取り直さないこと")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void confirmationDoesNotRetryOnOtherIntegrityViolations() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(orderService.confirmApplication(any(), any(), any()))
        .thenThrow(integrityViolation("uq_t_users_email"));

    // 写像を持たない違反は実装欠陥として全域ハンドラの分類（一意違反 = 409）へ落とす
    mockMvc
        .perform(storePost("/store/order-applications/a1/confirmation", CONFIRMATION_BODY))
        .andExpect(status().isConflict());

    verify(orderService, times(1)).confirmApplication(any(), any(), any());
  }

  @Test
  @DisplayName("列長を超える新規顧客の氏名・電話番号は 400 で撥ねられること（DB のエラーにしない）")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void confirmationWithAnOverlongNewCustomerIsRejected() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    String overlongName =
        "{\"business_date\": \"2026-08-20\", \"pax\": 2, \"new_customer\": {\"name\": \""
            + "あ".repeat(256)
            + "\"}}";
    mockMvc
        .perform(storePost("/store/order-applications/a1/confirmation", overlongName))
        .andExpect(status().isBadRequest());

    String overlongPhone =
        "{\"business_date\": \"2026-08-20\", \"pax\": 2, \"new_customer\": {\"name\": \"ゲスト花子\","
            + " \"phone_number\": \""
            + "1".repeat(51)
            + "\"}}";
    mockMvc
        .perform(storePost("/store/order-applications/a1/confirmation", overlongPhone))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(orderService);
  }

  @Test
  @DisplayName("受付箱の行に台帳・会計の項目が存在しないこと（申請は受注の前室に閉じる）")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void applicationRowCarriesOnlyApplicationFields() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(orderService.listApplications(any(), any(), anyInt()))
        .thenReturn(
            new CursorPage<>(
                List.of(OrderApplicationResponse.builder().id("a1").status("PENDING").build()),
                null));

    mockMvc
        .perform(storeGet("/store/order-applications?statuses=PENDING"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value("a1"))
        .andExpect(jsonPath("$.content[0].total_fee").doesNotExist());
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

  // ==================== 公開店面のゲスト予約申請 ====================

  private static final String GUEST_BODY =
      "{\"business_date\": \"2026-08-20\", \"pax\": 2,"
          + " \"contact_name\": \"ゲスト花子\", \"contact_phone_number\": \"09000000000\"}";

  @Test
  @DisplayName("ゲスト予約申請が受け付けられ、受付番号だけが 201 で返ること")
  void guestRequestIsAcceptedAndReturnsOnlyTheIdentifier() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(guestApplicationRateLimiter.tryConsume(any(), any())).thenReturn(true);
    when(guestOrderApplicationService.request(any(GuestOrderApplicationCreateRequest.class)))
        .thenReturn(new GuestOrderApplicationResponse("app-1"));

    mockMvc
        .perform(guestPost(GUEST_BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("app-1"))
        // 申請の内容は返さない — 送った本人だけが読めることを保証する手立てがこの経路には無い
        .andExpect(jsonPath("$.contact_phone_number").doesNotExist());
  }

  @Test
  @DisplayName("連絡先を欠いたゲスト予約申請が 400 で撥ねられること")
  void guestRequestWithoutContactIsRejected() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(guestApplicationRateLimiter.tryConsume(any(), any())).thenReturn(true);

    mockMvc
        .perform(guestPost("{\"business_date\": \"2026-08-20\", \"pax\": 2}"))
        .andExpect(status().isBadRequest());

    // 折返し先の無い申請は処理のしようがないため、行を起こす前に撥ねる
    verifyNoInteractions(guestOrderApplicationService);
  }

  @Test
  @DisplayName("列長を超えるゲスト予約申請の連絡先は 400 で撥ねられること（DB のエラーにしない）")
  void guestRequestWithAnOverlongContactIsRejected() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(guestApplicationRateLimiter.tryConsume(any(), any())).thenReturn(true);

    String overlongName =
        "{\"business_date\": \"2026-08-20\", \"pax\": 2, \"contact_name\": \""
            + "あ".repeat(256)
            + "\", \"contact_phone_number\": \"09000000000\"}";
    mockMvc.perform(guestPost(overlongName)).andExpect(status().isBadRequest());

    String overlongPhone =
        "{\"business_date\": \"2026-08-20\", \"pax\": 2, \"contact_name\": \"ゲスト花子\","
            + " \"contact_phone_number\": \""
            + "1".repeat(51)
            + "\"}";
    mockMvc.perform(guestPost(overlongPhone)).andExpect(status().isBadRequest());
    verifyNoInteractions(guestOrderApplicationService);
  }

  @Test
  @DisplayName("流量制限を超えたゲスト予約申請が 429 になり、申請が起きないこと")
  void guestRequestOverTheRateLimitIsRefused() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(guestApplicationRateLimiter.tryConsume(any(), any())).thenReturn(false);

    mockMvc.perform(guestPost(GUEST_BODY)).andExpect(status().isTooManyRequests());

    verifyNoInteractions(guestOrderApplicationService);
  }

  @Test
  @DisplayName("流量を数える発信元に X-Forwarded-For の末尾を使うこと（手前の要素は申請者が書ける）")
  void guestRequestCountsTheProxyAppendedHop() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(guestApplicationRateLimiter.tryConsume(any(), any())).thenReturn(true);
    when(guestOrderApplicationService.request(any(GuestOrderApplicationCreateRequest.class)))
        .thenReturn(new GuestOrderApplicationResponse("app-1"));

    mockMvc
        .perform(guestPost(GUEST_BODY).header("X-Forwarded-For", "10.0.0.9, 203.0.113.7"))
        .andExpect(status().isCreated());

    ArgumentCaptor<String> origin = ArgumentCaptor.forClass(String.class);
    verify(guestApplicationRateLimiter).tryConsume(any(), origin.capture());
    assertThat(origin.getValue()).isEqualTo("203.0.113.7");
  }

  /** 匿名の来訪者を模す。店舗文脈だけをヘッダで名乗り、認証主体は載せない。 */
  private MockHttpServletRequestBuilder guestPost(String body) {
    return post("/store/order-applications/public")
        .header("X-Role", "store")
        .header("X-Store-ID", "1")
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  // 本番では認証済み主体をサーブレットコンテナが載せるが、@WebMvcTest の最小チェーンでは載らないため明示する。
  private MockHttpServletRequestBuilder storePost(String path, String body) {
    return post(path)
        .header("X-Role", "store")
        .header("X-Store-ID", "1")
        .principal(() -> "staff@kizuna.test")
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  private MockHttpServletRequestBuilder storeGet(String path) {
    return get(path)
        .header("X-Role", "store")
        .header("X-Store-ID", "1")
        .principal(() -> "staff@kizuna.test");
  }
}
