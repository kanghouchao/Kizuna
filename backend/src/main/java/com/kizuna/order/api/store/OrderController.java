package com.kizuna.order.api.store;

import com.kizuna.order.api.dto.OrderAttributionInvalidationRequest;
import com.kizuna.order.api.dto.OrderAttributionResponse;
import com.kizuna.order.api.dto.OrderCastCandidateResponse;
import com.kizuna.order.api.dto.OrderCompletionPreviewResponse;
import com.kizuna.order.api.dto.OrderCompletionRequest;
import com.kizuna.order.api.dto.OrderCreateRequest;
import com.kizuna.order.api.dto.OrderReceiptTokenResponse;
import com.kizuna.order.api.dto.OrderReceptionistResponse;
import com.kizuna.order.api.dto.OrderResponse;
import com.kizuna.order.api.dto.OrderUpdateRequest;
import com.kizuna.order.api.dto.ReservationRequestUpdateRequest;
import com.kizuna.order.application.OrderAttributionService;
import com.kizuna.order.application.OrderService;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.IntegrityViolations;
import com.kizuna.shared.web.CursorPage;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<Page<OrderResponse>> list(
      @RequestParam(name = "customer_id", required = false) String customerId,
      @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
    return ResponseEntity.ok(orderService.list(customerId, pageable));
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

  @PostMapping
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<OrderResponse> create(@Valid @RequestBody OrderCreateRequest request) {
    return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
        .body(orderService.create(request));
  }

  /**
   * 予約受付 inbox の未確定申請一覧。並び（古い順）は読み口が固定するため、並び順の指定は受けない。
   *
   * <p>続きは応答の {@code next_cursor} をそのまま {@code cursor} に渡して取る。処理で行が消えていく一覧なので、
   * 位置を「何件目か」で指すと処理の直後に境界の申請を飛ばす。
   */
  @GetMapping("/reservation-requests")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<CursorPage<OrderResponse>> listReservationRequests(
      @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(orderService.listPendingReservationRequests(cursor, size));
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
    return ResponseEntity.ok(orderAttributionService.currentAttribution(id));
  }

  /**
   * 帰属記録を理由付きで無効化する（誤帰属の訂正）。帰属記録に対する唯一の訂正操作で、行は削除しない。
   *
   * <p>ポイント台帳へは波及しない。誤って付与されたポイントの清算は理由の残る手動調整で行う。
   */
  @PostMapping("/{id}/attribution/invalidation")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<OrderAttributionResponse> invalidateAttribution(
      @PathVariable String id,
      @Valid @RequestBody OrderAttributionInvalidationRequest request,
      Principal principal) {
    return ResponseEntity.ok(orderAttributionService.invalidate(id, request, principal.getName()));
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

  /** 完了処理の事前計算（会計金額に対する付与見込みと、会員なら残高）。 */
  @GetMapping("/{id}/completion-preview")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<OrderCompletionPreviewResponse> completionPreview(
      @PathVariable String id, @RequestParam(name = "total_fee") int totalFee) {
    return ResponseEntity.ok(orderService.completionPreview(id, totalFee));
  }

  /** 予約申請を謝絶する。 */
  @PostMapping("/{id}/decline")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<OrderResponse> decline(@PathVariable String id) {
    return ResponseEntity.ok(orderService.decline(id));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<OrderResponse> update(
      @PathVariable String id, @Valid @RequestBody OrderUpdateRequest request) {
    return ResponseEntity.ok(orderService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    orderService.delete(id);
    return ResponseEntity.ok().build();
  }
}
