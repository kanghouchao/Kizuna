package com.kizuna.order.api.store;

import com.kizuna.order.api.dto.OrderCreateRequest;
import com.kizuna.order.api.dto.OrderReceptionistResponse;
import com.kizuna.order.api.dto.OrderResponse;
import com.kizuna.order.api.dto.OrderUpdateRequest;
import com.kizuna.order.api.dto.ReservationRequestUpdateRequest;
import com.kizuna.order.application.OrderService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
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

  @PostMapping
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<OrderResponse> create(@Valid @RequestBody OrderCreateRequest request) {
    return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
        .body(orderService.create(request));
  }

  /** 予約受付 inbox の未確定申請一覧。並び（古い順）は読み口が固定するため、既定の sort は置かない。 */
  @GetMapping("/reservation-requests")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<Page<OrderResponse>> listReservationRequests(
      @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(orderService.listPendingReservationRequests(pageable));
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

  /** 予約申請を確定する（受注として受け付ける）。 */
  @PostMapping("/{id}/confirmation")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public ResponseEntity<OrderResponse> confirm(@PathVariable String id, Principal principal) {
    return ResponseEntity.ok(orderService.confirm(id, principal.getName()));
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
