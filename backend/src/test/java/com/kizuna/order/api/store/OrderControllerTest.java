package com.kizuna.order.api.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.order.api.dto.OrderArchiveResponse;
import com.kizuna.order.api.dto.OrderAttributionResponse;
import com.kizuna.order.api.dto.OrderCompletionPreviewResponse;
import com.kizuna.order.api.dto.OrderCompletionResponse;
import com.kizuna.order.api.dto.OrderCorrectionResponse;
import com.kizuna.order.api.dto.OrderReceiptTokenResponse;
import com.kizuna.order.api.dto.OrderSummaryResponse;
import com.kizuna.order.api.dto.OrderWorkQueueResponse;
import com.kizuna.order.application.OrderAttributionCorrectionService;
import com.kizuna.order.application.OrderAttributionService;
import com.kizuna.order.application.OrderCorrectionService;
import com.kizuna.order.application.OrderService;
import com.kizuna.order.domain.OrderQueryCriteria;
import com.kizuna.order.domain.OrderSortKey;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.store.application.StoreActivationService;
import java.time.LocalDate;
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

  /** 完了の要求本文。会計金額は受け取らず、会計の場で確定した内訳を送る。 */
  private static final String COMPLETION_BODY =
      "{\"fee_lines\":[{\"kind\":\"SURCHARGE\",\"name\":\"会計\",\"amount\":12000}]}";

  /** 完了後訂正の要求本文。門が直せる三組の全量を毎回送る（省略は「値なし」）。 */
  private static final String CORRECTION_BODY =
      "{\"expected_version\":3,\"reason\":\"金額の誤記\","
          + "\"actual_arrival_time\":\"20:15:00\",\"actual_end_time\":\"22:40:00\","
          + "\"course_name\":\"120 分コース\",\"course_minutes\":120,\"extension_minutes\":30,"
          + "\"fee_lines\":[{\"kind\":\"SURCHARGE\",\"name\":\"指名料\",\"amount\":15000}]}";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private OrderService orderService;
  @MockitoBean private OrderAttributionService orderAttributionService;
  @MockitoBean private OrderAttributionCorrectionService orderAttributionCorrectionService;
  @MockitoBean private OrderCorrectionService orderCorrectionService;

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
    Page<OrderSummaryResponse> empty = new PageImpl<>(List.of());
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
  @DisplayName("作業キューが要求された群・検索・並び・続きの位置を読み口へ渡すこと")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void workQueueForwardsTheRequestedCriteriaAndCursor() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    ArgumentCaptor<OrderQueryCriteria> criteriaCaptor =
        ArgumentCaptor.forClass(OrderQueryCriteria.class);
    ArgumentCaptor<String> cursorCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Integer> sizeCaptor = ArgumentCaptor.forClass(Integer.class);
    when(orderService.listWorkQueue(
            criteriaCaptor.capture(), cursorCaptor.capture(), sizeCaptor.capture()))
        .thenReturn(new CursorPage<>(List.of(), null));

    mockMvc
        .perform(
            get("/store/orders/work-queue?statuses=CONFIRMED,COMPLETED&customer_name=山田"
                    + "&business_date=2026-08-15&sort_key=PAX&desc=true&cursor=abc&size=5")
                .header("X-Role", "store")
                .header("X-Store-ID", "1"))
        .andExpect(status().isOk());

    assertThat(criteriaCaptor.getValue().statuses())
        .containsExactlyInAnyOrder(OrderStatus.CONFIRMED, OrderStatus.COMPLETED);
    assertThat(criteriaCaptor.getValue().customerName()).isEqualTo("山田");
    assertThat(criteriaCaptor.getValue().businessDate()).isEqualTo(LocalDate.of(2026, 8, 15));
    assertThat(criteriaCaptor.getValue().sortKey()).isEqualTo(OrderSortKey.PAX);
    assertThat(criteriaCaptor.getValue().descending()).isTrue();
    assertThat(cursorCaptor.getValue()).isEqualTo("abc");
    assertThat(sizeCaptor.getValue()).isEqualTo(5);
  }

  @Test
  @DisplayName("作業キューの行に伝票トークンとドライバー連絡事項のキーが存在しないこと")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void workQueueRowCarriesNeitherTheReceiptTokenNorTheDriverMessage() throws Exception {
    // 抑制ではなく型から消えていることを見る。NON_NULL 下では値が null でもキーは消えるので、
    // isNull() では「型に残ったまま詰め忘れただけ」の状態と区別できない。
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(orderService.listWorkQueue(any(), any(), anyInt()))
        .thenReturn(
            new CursorPage<>(
                List.of(OrderWorkQueueResponse.builder().id("o1").status("CONFIRMED").build()),
                null));

    mockMvc
        .perform(storeGet("/store/orders/work-queue?statuses=CONFIRMED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value("o1"))
        .andExpect(jsonPath("$.content[0].receipt_token").doesNotExist())
        .andExpect(jsonPath("$.content[0].cast_driver_message").doesNotExist());
  }

  @Test
  @DisplayName("アーカイブの行に伝票トークンとドライバー連絡事項のキーが存在しないこと")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void archiveRowCarriesNeitherTheReceiptTokenNorTheDriverMessage() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(orderService.listArchive(any(), any()))
        .thenReturn(
            new PageImpl<>(
                List.of(OrderArchiveResponse.builder().id("o1").status("COMPLETED").build())));

    mockMvc
        .perform(storeGet("/store/orders/archive?statuses=COMPLETED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value("o1"))
        .andExpect(jsonPath("$.content[0].receipt_token").doesNotExist())
        .andExpect(jsonPath("$.content[0].cast_driver_message").doesNotExist());
  }

  @Test
  @DisplayName("完了の応答は伝票トークンだけを持ち、受注の項目を載せないこと")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void completionResponseCarriesOnlyTheReceiptToken() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(orderService.complete(any(), any(), any()))
        .thenReturn(new OrderCompletionResponse("raw-token"));

    mockMvc
        .perform(storePost("/store/orders/o1/completion", COMPLETION_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.receipt_token").value("raw-token"))
        .andExpect(jsonPath("$.id").doesNotExist())
        .andExpect(jsonPath("$.status").doesNotExist());
  }

  @Test
  @DisplayName("明細の要素が null の完了要求は 400 で撥ねられること")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void completionRejectsANullFeeLineElement() throws Exception {
    // 要素の null は契約の段で止める。素通しすると写像が null を触って 500 に化ける
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(storePost("/store/orders/o1/completion", "{\"fee_lines\":[null]}"))
        .andExpect(status().isBadRequest());

    verify(orderService, never()).complete(any(), any(), any());
  }

  @Test
  @DisplayName("会員へ帰属した完了では伝票トークンのキーごと消えること")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void completionOfAMemberOrderOmitsTheReceiptTokenKey() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(orderService.complete(any(), any(), any())).thenReturn(new OrderCompletionResponse(null));

    mockMvc
        .perform(storePost("/store/orders/o1/completion", COMPLETION_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.receipt_token").doesNotExist());
  }

  @Test
  @DisplayName("続きの位置と検索を指定しない作業キューは先頭から絞り込みなしで読むこと")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void workQueueStartsFromTheBeginningWithoutACursor() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    ArgumentCaptor<OrderQueryCriteria> criteriaCaptor =
        ArgumentCaptor.forClass(OrderQueryCriteria.class);
    ArgumentCaptor<String> cursorCaptor = ArgumentCaptor.forClass(String.class);
    when(orderService.listWorkQueue(criteriaCaptor.capture(), cursorCaptor.capture(), anyInt()))
        .thenReturn(new CursorPage<>(List.of(), null));

    mockMvc
        .perform(
            get("/store/orders/work-queue?statuses=CONFIRMED&customer_name=")
                .header("X-Role", "store")
                .header("X-Store-ID", "1"))
        .andExpect(status().isOk());

    assertThat(cursorCaptor.getValue()).isNull();
    // 空欄の検索語は「送っていない」と同じ。述語を組むと全件が条件から漏れる
    assertThat(criteriaCaptor.getValue().customerName()).isNull();
    assertThat(criteriaCaptor.getValue().businessDate()).isNull();
  }

  @Test
  @DisplayName("アーカイブが群と並びを読み口へ渡し、ページャで辿れること")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void archiveForwardsTheRequestedCriteriaAndPage() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    ArgumentCaptor<OrderQueryCriteria> criteriaCaptor =
        ArgumentCaptor.forClass(OrderQueryCriteria.class);
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    when(orderService.listArchive(criteriaCaptor.capture(), pageableCaptor.capture()))
        .thenReturn(Page.empty());

    mockMvc
        .perform(
            get("/store/orders/archive?statuses=CANCELLED&sort_key=BUSINESS_DATE&page=2&size=5")
                .header("X-Role", "store")
                .header("X-Store-ID", "1"))
        .andExpect(status().isOk());

    assertThat(criteriaCaptor.getValue().statuses()).containsExactly(OrderStatus.CANCELLED);
    assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
    assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
  }

  @Test
  @DisplayName("受注管理権限が無ければ群読み口を読めないこと")
  @WithMockUser(authorities = "PERM_CUSTOMER_MANAGE")
  void groupReadsAreRejectedWithoutOrderManage() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(
            get("/store/orders/work-queue?statuses=CONFIRMED")
                .header("X-Role", "store")
                .header("X-Store-ID", "1"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get("/store/orders/archive?statuses=COMPLETED")
                .header("X-Role", "store")
                .header("X-Store-ID", "1"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(orderService);
  }

  @Test
  @DisplayName("受注管理権限があれば完了処理と事前計算を呼べること")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void completionAndPreviewAreAllowedForOrderManage() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(orderService.complete(any(), any(), any())).thenReturn(new OrderCompletionResponse(null));
    when(orderService.completionPreview(any(), anyInt()))
        .thenReturn(OrderCompletionPreviewResponse.builder().build());

    mockMvc
        .perform(storePost("/store/orders/o1/completion", COMPLETION_BODY))
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
        .perform(storePost("/store/orders/o1/completion", COMPLETION_BODY))
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
    when(orderService.update(any(), any())).thenReturn(OrderWorkQueueResponse.builder().build());

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
  @DisplayName("伝票トークンの再発行は新たな資源を生むので 201 で返ること")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void receiptTokenReissueRespondsCreated() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(orderAttributionService.reissueReceiptToken(any()))
        .thenReturn(new OrderReceiptTokenResponse("raw-token"));

    mockMvc
        .perform(storePost("/store/orders/o1/receipt-token"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.receipt_token").value("raw-token"));
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
  @DisplayName("列長を超える取消の理由は 400 で撥ねられること（DB のエラーにしない）")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void cancellationWithAnOverlongReasonIsRejected() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    String body = "{\"reason\": \"" + "あ".repeat(501) + "\"}";

    mockMvc
        .perform(storePost("/store/orders/o1/cancellation", body))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(orderService);

    // 正向対照: 上限ちょうどは通る（取消は結果を読まれない操作なので 204）
    mockMvc
        .perform(
            storePost("/store/orders/o1/cancellation", "{\"reason\": \"" + "あ".repeat(500) + "\"}"))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("理由の無い取消は 400 で撥ねられること")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void cancellationWithoutAReasonIsRejected() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    // 理由は取消の根拠そのもの。空白だけの理由も「書いていない」と同じに扱う
    mockMvc
        .perform(storePost("/store/orders/o1/cancellation", "{\"reason\": \"   \"}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(orderService);
  }

  @Test
  @DisplayName("受注管理権限が無ければ取消が拒否されること")
  @WithMockUser(authorities = "PERM_CUSTOMER_MANAGE")
  void cancellationIsRejectedWithoutOrderManage() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(storePost("/store/orders/o1/cancellation", "{\"reason\": \"客都合\"}"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(orderService);
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
        .thenReturn(
            new OrderAttributionResponse(null, false, null, null, null, null, null, null, null));
    mockMvc
        .perform(
            storePost(
                "/store/orders/o1/attribution/invalidation",
                "{\"attribution_id\": 1, \"reason\": \"" + "あ".repeat(500) + "\"}"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("完了後訂正は ORDER_CORRECT を持てば到達でき、新たな痕を生むので 201 で返ること")
  @WithMockUser(authorities = "PERM_ORDER_CORRECT")
  void correctionIsReachableWithOrderCorrect() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);
    when(orderCorrectionService.correct(any(), any(), any()))
        .thenReturn(new OrderCorrectionResponse(12000, 15000));

    mockMvc
        .perform(storePost("/store/orders/o1/corrections", CORRECTION_BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.previous_total_fee").value(12000))
        .andExpect(jsonPath("$.total_fee").value(15000));
  }

  @Test
  @DisplayName("受注管理だけの店員は完了後訂正に届かないこと")
  @WithMockUser(authorities = "PERM_ORDER_MANAGE")
  void correctionIsRejectedWithOrderManageAlone() throws Exception {
    // 日常権限で門を守ると「権限のある利用者のみが訂正できる」が空文になる
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    mockMvc
        .perform(storePost("/store/orders/o1/corrections", CORRECTION_BODY))
        .andExpect(status().isForbidden());
    verifyNoInteractions(orderCorrectionService);
  }

  @Test
  @DisplayName("凍結字段を載せた訂正は契約で撥ねられ、サービスへ届かないこと")
  @WithMockUser(authorities = "PERM_ORDER_CORRECT")
  void correctionCarryingAFrozenFieldIsRejected() throws Exception {
    // 凍結は要求の型に項目が無いことで成立する（未知の項目は撥ねられる設定）。指名は給与計算へ波及するため特に動かさない
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    for (String frozen : List.of("\"pax\":3", "\"cast_id\":\"c1\"", "\"remarks\":\"覚書\"")) {
      mockMvc
          .perform(
              storePost(
                  "/store/orders/o1/corrections",
                  "{\"expected_version\":3,\"reason\":\"金額の誤記\",\"fee_lines\":[]," + frozen + "}"))
          .andExpect(status().isBadRequest());
    }
    verifyNoInteractions(orderCorrectionService);

    // 正向対照: 凍結字段を外した同じ本文は通る（400 が「内訳が空だから」でない証明）
    when(orderCorrectionService.correct(any(), any(), any()))
        .thenReturn(new OrderCorrectionResponse(12000, 0));
    mockMvc
        .perform(
            storePost(
                "/store/orders/o1/corrections",
                "{\"expected_version\":3,\"reason\":\"金額の誤記\",\"fee_lines\":[]}"))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("理由の無い訂正と内訳を伴わない訂正は 400 で撥ねられること")
  @WithMockUser(authorities = "PERM_ORDER_CORRECT")
  void correctionWithoutAReasonOrFeeLinesIsRejected() throws Exception {
    when(storeExistenceCheck.exists(anyLong())).thenReturn(true);

    // 理由は凍結済みの記録を動かす根拠そのもの。空白だけの理由も「書いていない」と同じに扱う
    mockMvc
        .perform(
            storePost(
                "/store/orders/o1/corrections",
                "{\"expected_version\":3,\"reason\":\"  \",\"fee_lines\":[]}"))
        .andExpect(status().isBadRequest());
    // 内訳の省略を「内訳なし」として通すと、合計 0 の訂正が事故として成立する
    mockMvc
        .perform(
            storePost(
                "/store/orders/o1/corrections", "{\"expected_version\":3,\"reason\":\"金額の誤記\"}"))
        .andExpect(status().isBadRequest());
    // 版を載せない要求も撥ねる。全量置換の口は「画面が見ていた版」が無いと食い違いを検出できない
    mockMvc
        .perform(
            storePost("/store/orders/o1/corrections", "{\"reason\":\"金額の誤記\",\"fee_lines\":[]}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(orderCorrectionService);
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
