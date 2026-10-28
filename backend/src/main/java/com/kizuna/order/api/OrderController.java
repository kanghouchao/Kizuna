package com.kizuna.order.api;

import com.kizuna.order.api.dto.BusinessContactHistoryResponse;
import com.kizuna.order.api.dto.OrderArchiveResponse;
import com.kizuna.order.api.dto.OrderAttributionCorrectionRequest;
import com.kizuna.order.api.dto.OrderAttributionCorrectionResponse;
import com.kizuna.order.api.dto.OrderAttributionInvalidationRequest;
import com.kizuna.order.api.dto.OrderAttributionResponse;
import com.kizuna.order.api.dto.OrderCancellationRequest;
import com.kizuna.order.api.dto.OrderCastCandidateResponse;
import com.kizuna.order.api.dto.OrderCompletionInvalidationRequest;
import com.kizuna.order.api.dto.OrderCompletionRequest;
import com.kizuna.order.api.dto.OrderCompletionResponse;
import com.kizuna.order.api.dto.OrderCorrectionRequest;
import com.kizuna.order.api.dto.OrderCorrectionResponse;
import com.kizuna.order.api.dto.OrderCreateRequest;
import com.kizuna.order.api.dto.OrderCustomerCandidateResponse;
import com.kizuna.order.api.dto.OrderMapper;
import com.kizuna.order.api.dto.OrderPointRollbackPreviewResponse;
import com.kizuna.order.api.dto.OrderPointRollbackRequest;
import com.kizuna.order.api.dto.OrderPointRollbackResponse;
import com.kizuna.order.api.dto.OrderPreviewResponse;
import com.kizuna.order.api.dto.OrderReceiptTokenResponse;
import com.kizuna.order.api.dto.OrderReceptionistResponse;
import com.kizuna.order.api.dto.OrderResponse;
import com.kizuna.order.api.dto.OrderSummaryResponse;
import com.kizuna.order.api.dto.OrderUpdateRequest;
import com.kizuna.order.api.dto.OrderWorkQueueResponse;
import com.kizuna.order.application.BusinessContactPermissions;
import com.kizuna.order.application.OrderAttributionCorrectionService;
import com.kizuna.order.application.OrderAttributionService;
import com.kizuna.order.application.OrderCorrectionHistory;
import com.kizuna.order.application.OrderCorrectionService;
import com.kizuna.order.application.OrderPointRollbackService;
import com.kizuna.order.application.OrderService;
import com.kizuna.order.domain.OrderQueryCriteria;
import com.kizuna.order.domain.OrderSortKey;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.order.result.OrderCorrectionResult;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.IntegrityViolations;
import com.kizuna.shared.storescope.CurrentStore;
import com.kizuna.shared.web.CursorPage;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

  private final OrderCorrectionHistory correctionHistory;

  private final OrderService orderService;
  private final BusinessContactPermissions businessContactPermissions;
  private final OrderAttributionService orderAttributionService;
  private final OrderAttributionCorrectionService orderAttributionCorrectionService;
  private final OrderCorrectionService orderCorrectionService;
  private final OrderPointRollbackService orderPointRollbackService;
  private final OrderMapper orderMapper;

  /** 顧客詳細の注文履歴。ある顧客に着いた受注を新しい順に辿る（状態は問わない）。 */
  @GetMapping
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<Page<OrderSummaryResponse>> list(
      @CurrentStore Long storeId,
      @RequestParam(name = "customer_id", required = false) String customerId,
      @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
    return ResponseEntity.ok(
        orderService.list(storeId, customerId, pageable).map(orderMapper::toSummaryResponse));
  }

  /**
   * 作業キュー（対応が要る受注）の読み口。状態の群を指定して、検索と並び替えを当てたうえでカーソルで辿る。
   *
   * <p>続きは応答の {@code next_cursor} をそのまま {@code cursor} に渡して取る。確定・取消で行が消えていく一覧なので、
   * 位置を「何件目か」で指すと処理の直後に境界の受注を飛ばす。
   *
   * <p>状態を要求で受けるのは、画面の群がその軸そのものだからである（検索条件には状態を持たせない）。
   */
  @GetMapping("/work-queue")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<CursorPage<OrderWorkQueueResponse>> listWorkQueue(
      @CurrentStore Long storeId,
      @RequestParam(name = "statuses") Set<OrderStatus> statuses,
      @RequestParam(name = "customer_name", required = false) String customerName,
      @RequestParam(name = "business_date", required = false) @DateTimeFormat(iso = ISO.DATE)
          LocalDate businessDate,
      @RequestParam(name = "sort_key", defaultValue = "BUSINESS_DATE") OrderSortKey sortKey,
      @RequestParam(name = "desc", defaultValue = "false") boolean descending,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(
        orderService
            .listWorkQueue(
                storeId,
                criteria(statuses, customerName, businessDate, sortKey, descending),
                cursor,
                size)
            .map(orderMapper::toWorkQueueResponse));
  }

  /**
   * アーカイブ（完了・取消）の読み口。作業キューと同じ検索・並び替えを、オフセットのページャで辿る。
   *
   * <p>位置をページ番号で指せるのは、終端状態の受注が処理で消えず増えるだけだからである。
   */
  @GetMapping("/archive")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<Page<OrderArchiveResponse>> listArchive(
      @CurrentStore Long storeId,
      @RequestParam(name = "statuses") Set<OrderStatus> statuses,
      @RequestParam(name = "customer_name", required = false) String customerName,
      @RequestParam(name = "business_date", required = false) @DateTimeFormat(iso = ISO.DATE)
          LocalDate businessDate,
      @RequestParam(name = "sort_key", defaultValue = "BUSINESS_DATE") OrderSortKey sortKey,
      @RequestParam(name = "desc", defaultValue = "true") boolean descending,
      @PageableDefault(
              size = 20,
              sort = {})
          Pageable pageable) {
    return ResponseEntity.ok(
        orderService
            .listArchive(
                storeId,
                criteria(statuses, customerName, businessDate, sortKey, descending),
                pageable)
            .map(orderMapper::toArchiveResponse));
  }

  /** 空文字の検索語は「送っていない」と同じに扱う。画面の検索欄をクリアした状態がそのまま乗ってくるため。 */
  private OrderQueryCriteria criteria(
      Set<OrderStatus> statuses,
      String customerName,
      LocalDate businessDate,
      OrderSortKey sortKey,
      boolean descending) {
    String search = (customerName == null || customerName.isBlank()) ? null : customerName.trim();
    return new OrderQueryCriteria(statuses, search, businessDate, sortKey, descending);
  }

  @GetMapping("/customer-candidates")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public CursorPage<OrderCustomerCandidateResponse> customerCandidates(
      @CurrentStore Long storeId,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size,
      Authentication authentication) {
    return orderService
        .customerCandidates(
            storeId, search, cursor, size, CustomerManagementAuthority.held(authentication))
        .map(orderMapper::toResponse);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<OrderResponse> get(@CurrentStore Long storeId, @PathVariable String id) {
    return ResponseEntity.ok(orderMapper.toResponse(orderService.get(storeId, id)));
  }

  @GetMapping("/{id}/business-contact-permission-history")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public CursorPage<BusinessContactHistoryResponse> businessContactHistory(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return businessContactPermissions
        .history(storeId, id, cursor, size)
        .map(orderMapper::toResponse);
  }

  @GetMapping("/receptionists")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<List<OrderReceptionistResponse>> listReceptionists(
      @CurrentStore Long storeId) {
    return ResponseEntity.ok(
        orderService.listReceptionists(storeId).stream().map(orderMapper::toResponse).toList());
  }

  /**
   * 指名候補の一覧（当店に在籍中のキャスト）。キャスト管理の一覧ではなくこの読み口を持つのは、指名が受注の操作で、候補の範囲も要る権限も受注側が決めるため。
   *
   * <p>件数上限と並びは読み口が固定する。絞り込みは名前で行うため、続きを辿る手段は持たせない。
   */
  @GetMapping("/cast-candidates")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<List<OrderCastCandidateResponse>> listCastCandidates(
      @CurrentStore Long storeId, @RequestParam(required = false) String search) {
    return ResponseEntity.ok(
        orderService.listCastCandidates(storeId, search).stream()
            .map(orderMapper::toResponse)
            .toList());
  }

  /**
   * 受注を作成する。この経路の受注は確定で出生し、受付経路に Web 申請の経路は指定できない。
   *
   * <p>受付担当を省略した要求では実行者本人が受付担当になる。そのため認証主体をサービスへ渡す。
   */
  @PostMapping
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<OrderResponse> create(
      @CurrentStore Long storeId,
      @Valid @RequestBody OrderCreateRequest request,
      Authentication authentication) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            orderMapper.toResponse(
                orderService.create(
                    storeId,
                    orderMapper.toCommand(request),
                    authentication.getName(),
                    CustomerManagementAuthority.held(authentication))));
  }

  /** 受注を完了する（会計の確定）。ポイントの付与・利用はこの経路でのみ台帳へ入る。 */
  @PostMapping("/{id}/completion")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<OrderCompletionResponse> complete(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @Valid @RequestBody OrderCompletionRequest request,
      Principal principal) {
    var result =
        orderService.complete(storeId, id, orderMapper.toCommand(request), principal.getName());
    return ResponseEntity.ok(new OrderCompletionResponse(result.receiptToken()));
  }

  /** 受注 1 件の帰属の現況（会員コード・成立の機構・無効化の理由）。無効化と再発行のどちらを提示するかはこの読み口で決まる。 */
  @GetMapping("/{id}/attribution")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<OrderAttributionResponse> attribution(
      @CurrentStore Long storeId, @PathVariable String id) {
    return ResponseEntity.ok(
        withPendingCorrection(
            storeId,
            id,
            orderMapper.toResponse(orderAttributionService.currentAttribution(storeId, id))));
  }

  /**
   * 現況に「まだ差し引かれていない誤付与」を添える。
   *
   * <p>2 つのサービスを跨ぐ組み立てをここで行うのは、無効化のサービスが台帳へ依存しないこと自体が「無効化は台帳へ 波及しない」（ADR
   * 0009）の構造的な証跡だからである。あちらへ台帳の読みを足すとその証跡が失われる。
   */
  private OrderAttributionResponse withPendingCorrection(
      Long storeId, String orderId, OrderAttributionResponse current) {
    return orderAttributionCorrectionService
        .findPendingCorrection(storeId, orderId)
        .map(
            pending -> current.withPendingCorrection(pending.attributionId(), pending.memberCode()))
        .orElse(current);
  }

  /**
   * 帰属記録を理由付きで無効化する（誤帰属の訂正の一段目）。帰属記録に対する唯一の訂正操作で、行は削除しない。
   *
   * <p>ポイント台帳へは波及しない。誤って付与されたポイントは二段目の訂正で差し引く（ADR 0012）。
   *
   * <p>権限が {@code POINT_ADJUST} なのは、これが不可逆で準金銭的な操作の一段目だからである。一段目だけを店員に許すと、
   * 二段目を実行できない者が訂正を始められ、やり残しが人を跨いで残る。
   */
  @PostMapping("/{id}/attribution/invalidation")
  @PreAuthorize("hasAuthority('PERM_POINT_ADJUST')")
  public ResponseEntity<OrderAttributionResponse> invalidateAttribution(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @Valid @RequestBody OrderAttributionInvalidationRequest request,
      Principal principal) {
    // 無効化の直後は必ず差し引きが残る。現況の読み口と同じ項目を載せておかないと、画面が「後で行う」で
    // 戻った先で差し引きへの入口を失う。
    return ResponseEntity.ok(
        withPendingCorrection(
            storeId,
            id,
            orderMapper.toResponse(
                orderAttributionService.invalidate(
                    storeId,
                    id,
                    request.getAttributionId(),
                    request.getReason(),
                    principal.getName()))));
  }

  /**
   * 無効化された受注へ伝票トークンを再発行する。正しい本人が所持証明で来店を取り戻すための唯一の経路。
   *
   * <p>申領期限は再発行から 90 日で数え直す。生値はこの応答にしか現れない。
   */
  @PostMapping("/{id}/receipt-token")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<OrderReceiptTokenResponse> reissueReceiptToken(
      @CurrentStore Long storeId, @PathVariable String id) {
    // 巻き戻し済みの受注へは発行しない。発行しても申領は拒まれるので、店舗は成功したと告げられたうえで
    // 使えない QR を客へ見せることになる。判定と発行は受注行を押さえたまま 1 つの取引で行う。
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            orderMapper.toReceiptTokenResponse(
                orderPointRollbackService.reissueReceiptTokenUnlessRolledBack(storeId, id)));
  }

  /** 誤帰属の訂正の進み具合（この受注がその会員へ与えた付与の合計と、既に差し引いた合計）。画面の既定値はここから取る。 */
  @GetMapping("/{id}/attribution/correction")
  @PreAuthorize("hasAuthority('PERM_POINT_ADJUST')")
  public ResponseEntity<OrderAttributionCorrectionResponse> correctionStatus(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @RequestParam(name = "attribution_id") Long attributionId) {
    return ResponseEntity.ok(
        orderMapper.toResponse(
            orderAttributionCorrectionService.status(storeId, id, attributionId)));
  }

  /**
   * 誤帰属で付いたポイントを、名指された帰属記録が持つ会員から差し引く（訂正の二段目。ADR 0012）。
   *
   * <p>同時再送で冪等キーの一意制約に敗れた場合だけ、初回の結果を読み直す再送処理へ回す（ADR 0007）。この分岐はサービスの トランザクション境界の外に置かなければならない —
   * 制約違反の時点で敗者のトランザクションは作廃されており、内側で catch しても読み直せない。他の整合性違反は実装欠陥なのでそのまま上げ、全域ハンドラの分類に委ねる。
   */
  @PostMapping("/{id}/attribution/correction")
  @PreAuthorize("hasAuthority('PERM_POINT_ADJUST')")
  public ResponseEntity<OrderAttributionCorrectionResponse> correctAttribution(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @Valid @RequestBody OrderAttributionCorrectionRequest request,
      Principal principal) {
    try {
      return ResponseEntity.ok(
          orderMapper.toResponse(
              orderAttributionCorrectionService.correct(
                  storeId, id, orderMapper.toCommand(request), principal.getName())));
    } catch (DataIntegrityViolationException ex) {
      if (!IntegrityViolations.violates(ex, DbConstraint.UQ_T_POINT_ENTRIES_IDEMPOTENCY_KEY)) {
        throw ex;
      }
      return ResponseEntity.ok(
          orderMapper.toResponse(
              orderAttributionCorrectionService.replayCorrect(
                  storeId, id, orderMapper.toCommand(request), principal.getName())));
    }
  }

  /** ポイント巻き戻しの下見（実行前に動く量を示す）。既に巻き戻し済みなら、その旨だけが真で量は 0 になる。 */
  @GetMapping("/{id}/point-rollback-preview")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE') and hasAuthority('PERM_POINT_ADJUST')")
  public ResponseEntity<OrderPointRollbackPreviewResponse> pointRollbackPreview(
      @CurrentStore Long storeId, @PathVariable String id) {
    return ResponseEntity.ok(
        orderMapper.toResponse(orderPointRollbackService.preview(storeId, id)));
  }

  /**
   * 受注 1 件を根拠とするポイントの授受を理由付きで打ち消す（巻き戻し）。
   *
   * <p>新たな操作記録を生む動作なので 201（完了後訂正・伝票トークン発行と同じ扱い）。
   *
   * <p>二度目は 409。同じ受注への並行要求は受注行のロックで直列化されるためサービス側の判定が撥ねるが、 一意制約に敗れる形で届いた場合も同じ 409 へ落とす（500
   * に化けさせない）。初回の理由・実行者は書き換わらない。
   */
  @PostMapping("/{id}/point-rollback")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE') and hasAuthority('PERM_POINT_ADJUST')")
  public ResponseEntity<OrderPointRollbackResponse> pointRollback(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @Valid @RequestBody OrderPointRollbackRequest request,
      Principal principal) {
    try {
      return ResponseEntity.status(HttpStatus.CREATED)
          .body(
              orderMapper.toResponse(
                  orderPointRollbackService.rollback(
                      storeId, id, orderMapper.toCommand(request), principal.getName())));
    } catch (DataIntegrityViolationException ex) {
      if (!IntegrityViolations.violates(ex, DbConstraint.UQ_T_POINT_ROLLBACKS_ORDER)) {
        throw ex;
      }
      throw new ConflictException("この受注のポイントは既に巻き戻されています");
    }
  }

  /** 完了処理の事前計算（会計金額に対する付与見込みと、会員なら残高）。 */
  @PostMapping("/{id}/completion-preview")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<OrderPreviewResponse> completionPreview(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @Valid @RequestBody OrderCompletionRequest request,
      Principal principal) {
    return ResponseEntity.ok(
        orderMapper.toPreviewResponse(
            orderService.completionPreview(
                storeId, id, orderMapper.toCommand(request), principal.getName())));
  }

  /** 未完了（CONFIRMED / IN_SERVICE）の受注を理由付きで取消す。二度目の取消は状態違反（400）として拒否する。 */
  @PostMapping("/{id}/cancellation")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<Void> cancel(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @Valid @RequestBody OrderCancellationRequest request,
      Principal principal) {
    orderService.cancel(storeId, id, request.getReason(), principal.getName());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/completion-invalidation")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE') and hasAuthority('PERM_ORDER_CORRECT')")
  public ResponseEntity<OrderCorrectionResult> invalidateCompletion(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @Valid @RequestBody OrderCompletionInvalidationRequest request,
      Principal principal) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            orderCorrectionService.invalidateCompletion(
                storeId, id, request.expectedVersion(), request.reason(), principal.getName()));
  }

  /** 完了受注の明細・実績時刻・コース・特殊サービスを理由付きで訂正する。 状態・ポイントを変えず、訂正前後の採用条件・金額・分数・報酬を返す。 */
  @PostMapping("/{id}/corrections")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE') and hasAuthority('PERM_ORDER_CORRECT')")
  public ResponseEntity<OrderCorrectionResponse> correct(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @Valid @RequestBody OrderCorrectionRequest request,
      Principal principal) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            orderMapper.toResponse(
                orderCorrectionService.correct(
                    storeId, id, orderMapper.toInput(request), principal.getName())));
  }

  /**
   * 受注の内容を部分更新する。状態は動かさない — 完了・取消はいずれも専用の操作が独占する。
   *
   * <p>終端状態（完了・取消）の受注は撥ねられる。受注 1 件を名指して消す口は無い（誤登録も取消として記録に残す。ADR 0013）。
   */
  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<OrderResponse> update(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @Valid @RequestBody OrderUpdateRequest request,
      Authentication authentication) {
    return ResponseEntity.ok(
        orderMapper.toResponse(
            orderService.update(
                storeId,
                id,
                orderMapper.toCommand(request),
                authentication.getName(),
                CustomerManagementAuthority.held(authentication))));
  }

  @PostMapping("/preview")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public OrderPreviewResponse previewCreate(
      @CurrentStore Long storeId,
      @Valid @RequestBody OrderCreateRequest request,
      Authentication authentication) {
    return orderMapper.toPreviewResponse(
        orderService.previewCreate(
            storeId,
            orderMapper.toCommand(request),
            authentication.getName(),
            CustomerManagementAuthority.held(authentication)));
  }

  @PostMapping("/{id}/preview")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public OrderPreviewResponse previewUpdate(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @Valid @RequestBody OrderUpdateRequest request,
      Authentication authentication) {
    return orderMapper.toPreviewResponse(
        orderService.previewUpdate(
            storeId,
            id,
            orderMapper.toCommand(request),
            authentication.getName(),
            CustomerManagementAuthority.held(authentication)));
  }

  @PostMapping("/{id}/correction-preview")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE') and hasAuthority('PERM_ORDER_CORRECT')")
  public OrderPreviewResponse previewCorrection(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @Valid @RequestBody OrderCorrectionRequest request,
      Principal principal) {
    return orderMapper.toPreviewResponse(
        orderCorrectionService.preview(
            storeId, id, orderMapper.toInput(request), principal.getName()));
  }

  /** 訂正履歴。横断一覧（/orders/cross-store）からも行の店舗を X-Store-ID に名指して読むので、横断照会者も通す。 */
  @GetMapping("/{id}/corrections")
  @PreAuthorize("hasAnyAuthority('PERM_ORDER_MANAGE', 'PERM_ORDER_SET_VIEW')")
  public CursorPage<OrderCorrectionResult> corrections(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return correctionHistory.store(storeId, id, cursor, size);
  }
}
