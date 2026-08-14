package com.kizuna.order.api.store;

import com.kizuna.order.api.dto.OrderAttributionCorrectionRequest;
import com.kizuna.order.api.dto.OrderAttributionCorrectionResponse;
import com.kizuna.order.api.dto.OrderAttributionInvalidationRequest;
import com.kizuna.order.api.dto.OrderAttributionResponse;
import com.kizuna.order.api.dto.OrderCancellationRequest;
import com.kizuna.order.api.dto.OrderCastCandidateResponse;
import com.kizuna.order.api.dto.OrderCompletionPreviewResponse;
import com.kizuna.order.api.dto.OrderCompletionRequest;
import com.kizuna.order.api.dto.OrderCreateRequest;
import com.kizuna.order.api.dto.OrderReceiptTokenResponse;
import com.kizuna.order.api.dto.OrderReceptionistResponse;
import com.kizuna.order.api.dto.OrderResponse;
import com.kizuna.order.api.dto.OrderUpdateRequest;
import com.kizuna.order.api.dto.ReservationRequestUpdateRequest;
import com.kizuna.order.application.OrderAttributionCorrectionService;
import com.kizuna.order.application.OrderAttributionService;
import com.kizuna.order.application.OrderService;
import com.kizuna.order.domain.OrderQueryCriteria;
import com.kizuna.order.domain.OrderSortKey;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.IntegrityViolations;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/store/orders")
@RequiredArgsConstructor
public class OrderController {

  private final OrderService orderService;
  private final OrderAttributionService orderAttributionService;
  private final OrderAttributionCorrectionService orderAttributionCorrectionService;

  /** 顧客詳細の注文履歴。ある顧客に着いた受注を新しい順に辿る（状態は問わない）。 */
  @GetMapping
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<Page<OrderResponse>> list(
      @RequestParam(name = "customer_id", required = false) String customerId,
      @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
    return ResponseEntity.ok(orderService.list(customerId, pageable));
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
  public ResponseEntity<CursorPage<OrderResponse>> listWorkQueue(
      @RequestParam(name = "statuses") Set<OrderStatus> statuses,
      @RequestParam(name = "customer_name", required = false) String customerName,
      @RequestParam(name = "business_date", required = false) @DateTimeFormat(iso = ISO.DATE)
          LocalDate businessDate,
      @RequestParam(name = "sort", defaultValue = "BUSINESS_DATE") OrderSortKey sort,
      @RequestParam(name = "desc", defaultValue = "false") boolean descending,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(
        orderService.listWorkQueue(
            criteria(statuses, customerName, businessDate, sort, descending), cursor, size));
  }

  /**
   * アーカイブ（完了・取消）の読み口。作業キューと同じ検索・並び替えを、オフセットのページャで辿る。
   *
   * <p>位置をページ番号で指せるのは、終端状態の受注が処理で消えず増えるだけだからである。
   */
  @GetMapping("/archive")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<Page<OrderResponse>> listArchive(
      @RequestParam(name = "statuses") Set<OrderStatus> statuses,
      @RequestParam(name = "customer_name", required = false) String customerName,
      @RequestParam(name = "business_date", required = false) @DateTimeFormat(iso = ISO.DATE)
          LocalDate businessDate,
      @RequestParam(name = "sort", defaultValue = "BUSINESS_DATE") OrderSortKey sort,
      @RequestParam(name = "desc", defaultValue = "true") boolean descending,
      @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(
        orderService.listArchive(
            criteria(statuses, customerName, businessDate, sort, descending), pageable));
  }

  /** 空文字の検索語は「送っていない」と同じに扱う。画面の検索欄をクリアした状態がそのまま乗ってくるため。 */
  private OrderQueryCriteria criteria(
      Set<OrderStatus> statuses,
      String customerName,
      LocalDate businessDate,
      OrderSortKey sort,
      boolean descending) {
    String search = (customerName == null || customerName.isBlank()) ? null : customerName.trim();
    return new OrderQueryCriteria(statuses, search, businessDate, sort, descending);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<OrderResponse> get(@PathVariable String id) {
    return ResponseEntity.ok(orderService.get(id));
  }

  @GetMapping("/receptionists")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<List<OrderReceptionistResponse>> listReceptionists() {
    return ResponseEntity.ok(orderService.listReceptionists());
  }

  /**
   * 指名候補の一覧（当店に在籍中のキャスト）。キャスト管理の一覧ではなくこの読み口を持つのは、指名が受注の操作で、候補の範囲も要る権限も受注側が決めるため。
   *
   * <p>件数上限と並びは読み口が固定する。絞り込みは名前で行うため、続きを辿る手段は持たせない。
   */
  @GetMapping("/cast-candidates")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<List<OrderCastCandidateResponse>> listCastCandidates(
      @RequestParam(required = false) String search) {
    return ResponseEntity.ok(orderService.listCastCandidates(search));
  }

  /**
   * 受注を作成する。この経路の受注は確定で出生し、受付経路に WEB は指定できない。
   *
   * <p>受付担当を省略した要求では実行者本人が受付担当になる。そのため認証主体をサービスへ渡す。
   */
  @PostMapping
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<OrderResponse> create(
      @Valid @RequestBody OrderCreateRequest request, Principal principal) {
    return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
        .body(orderService.create(request, principal.getName()));
  }

  /**
   * 未確定の予約申請を編集する。指名・受付担当を可空で扱う専用の契約で、汎用更新（{@link #update}）の必須項目に縛られずに
   * 人数・備考を直したり指名を外したりできる。受け取った内容がそのまま新しい申請内容になる。
   */
  @PutMapping("/reservation-requests/{id}")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<OrderResponse> updateReservationRequest(
      @PathVariable String id, @Valid @RequestBody ReservationRequestUpdateRequest request) {
    return ResponseEntity.ok(orderService.updateReservationRequest(id, request));
  }

  /**
   * 予約申請を確定する（受注として受け付ける）。
   *
   * <p>確定は当店の台帳行と関連を自動で整えるため、同一会員・同一店舗の申請 2 件が同時に確定すると双方が「関連なし」を観測し、遅い側が関連の部分一意索引に敗れる。 敗者は取り直す —
   * 勝者の関連は既に commit されているので、2 度目の確定は自動整備の再利用の枝へ落ちて収束する。
   *
   * <p>この分岐はサービスのトランザクション境界の外に置かなければならない（ADR 0007 と同じ紀律）— 制約違反の時点で敗者のトランザクションは作廃されており、内側で catch
   * しても勝者の関連を読み直せない。取り直しは 1 度だけで、それでも敗れる場合（勝者の関連がその間に解除される等）は一意違反として 409
   * に落ちる。他の整合性違反は実装欠陥なのでそのまま上げる。
   */
  @PostMapping("/{id}/confirmation")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<OrderResponse> confirm(@PathVariable String id, Principal principal) {
    try {
      return ResponseEntity.ok(orderService.confirm(id, principal.getName()));
    } catch (DataIntegrityViolationException ex) {
      if (!IntegrityViolations.violates(
          ex, DbConstraint.UQ_T_CUSTOMER_MEMBER_LINKS_ACTIVE_MEMBER)) {
        throw ex;
      }
      return ResponseEntity.ok(orderService.confirm(id, principal.getName()));
    }
  }

  /** 受注を完了する（会計の確定）。ポイントの付与・利用はこの経路でのみ台帳へ入る。 */
  @PostMapping("/{id}/completion")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<OrderResponse> complete(
      @PathVariable String id,
      @Valid @RequestBody OrderCompletionRequest request,
      Principal principal) {
    return ResponseEntity.ok(orderService.complete(id, request, principal.getName()));
  }

  /** 受注 1 件の帰属の現況（会員コード・成立の機構・無効化の理由）。無効化と再発行のどちらを提示するかはこの読み口で決まる。 */
  @GetMapping("/{id}/attribution")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<OrderAttributionResponse> attribution(@PathVariable String id) {
    return ResponseEntity.ok(
        withPendingCorrection(id, orderAttributionService.currentAttribution(id)));
  }

  /**
   * 現況に「まだ差し引かれていない誤付与」を添える。
   *
   * <p>2 つのサービスを跨ぐ組み立てをここで行うのは、無効化のサービスが台帳へ依存しないこと自体が「無効化は台帳へ 波及しない」（ADR
   * 0009）の構造的な証跡だからである。あちらへ台帳の読みを足すとその証跡が失われる。
   */
  private OrderAttributionResponse withPendingCorrection(
      String orderId, OrderAttributionResponse current) {
    return orderAttributionCorrectionService
        .findPendingCorrection(orderId)
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
      @PathVariable String id,
      @Valid @RequestBody OrderAttributionInvalidationRequest request,
      Principal principal) {
    // 無効化の直後は必ず差し引きが残る。現況の読み口と同じ項目を載せておかないと、画面が「後で行う」で
    // 戻った先で差し引きへの入口を失う。
    return ResponseEntity.ok(
        withPendingCorrection(
            id, orderAttributionService.invalidate(id, request, principal.getName())));
  }

  /**
   * 無効化された受注へ伝票トークンを再発行する。正しい本人が所持証明で来店を取り戻すための唯一の経路。
   *
   * <p>申領期限は再発行から 90 日で数え直す。生値はこの応答にしか現れない。
   */
  @PostMapping("/{id}/receipt-token")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<OrderReceiptTokenResponse> reissueReceiptToken(@PathVariable String id) {
    return ResponseEntity.ok(orderAttributionService.reissueReceiptToken(id));
  }

  /** 誤帰属の訂正の進み具合（この受注がその会員へ与えた付与の合計と、既に差し引いた合計）。画面の既定値はここから取る。 */
  @GetMapping("/{id}/attribution/correction")
  @PreAuthorize("hasAuthority('PERM_POINT_ADJUST')")
  public ResponseEntity<OrderAttributionCorrectionResponse> correctionStatus(
      @PathVariable String id, @RequestParam(name = "attribution_id") Long attributionId) {
    return ResponseEntity.ok(orderAttributionCorrectionService.status(id, attributionId));
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
      @PathVariable String id,
      @Valid @RequestBody OrderAttributionCorrectionRequest request,
      Principal principal) {
    try {
      return ResponseEntity.ok(
          orderAttributionCorrectionService.correct(id, request, principal.getName()));
    } catch (DataIntegrityViolationException ex) {
      if (!IntegrityViolations.violates(ex, DbConstraint.UQ_T_POINT_ENTRIES_IDEMPOTENCY_KEY)) {
        throw ex;
      }
      return ResponseEntity.ok(
          orderAttributionCorrectionService.replayCorrect(id, request, principal.getName()));
    }
  }

  /** 完了処理の事前計算（会計金額に対する付与見込みと、会員なら残高）。 */
  @GetMapping("/{id}/completion-preview")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<OrderCompletionPreviewResponse> completionPreview(
      @PathVariable String id, @RequestParam(name = "total_fee") int totalFee) {
    return ResponseEntity.ok(orderService.completionPreview(id, totalFee));
  }

  /** 予約申請を謝絶する。確定後の取消は理由必須の専用操作（{@link #cancel}）が受け持つ。 */
  @PostMapping("/{id}/decline")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<OrderResponse> decline(@PathVariable String id) {
    return ResponseEntity.ok(orderService.decline(id));
  }

  /**
   * 確定済みの受注を理由付きで取消す。理由・実行者・時刻が記録に残り、以後この受注は終端状態として凍結される（ADR 0013）。
   *
   * <p>対象は確定済みの受注のみ。未確定の申請は謝絶が、誤って完了した受注の救済は（まだ存在しない）別の経路が受け持つ。 二度目の取消は撥ねられる（逐次なら集約の守衛で
   * 400、同時なら楽観ロックで 409）。
   */
  @PostMapping("/{id}/cancellation")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<OrderResponse> cancel(
      @PathVariable String id,
      @Valid @RequestBody OrderCancellationRequest request,
      Principal principal) {
    return ResponseEntity.ok(orderService.cancel(id, request, principal.getName()));
  }

  /**
   * 受注の内容を部分更新する。状態は動かさない — 完了・取消・確定・謝絶はいずれも専用の操作が独占する。
   *
   * <p>終端状態（完了・取消）の受注は撥ねられる。受注 1 件を名指して消す口は無い（誤登録も取消として記録に残す。ADR 0013）。
   */
  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<OrderResponse> update(
      @PathVariable String id, @Valid @RequestBody OrderUpdateRequest request) {
    return ResponseEntity.ok(orderService.update(id, request));
  }
}
