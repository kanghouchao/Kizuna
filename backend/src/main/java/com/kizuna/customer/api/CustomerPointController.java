package com.kizuna.customer.api;

import com.kizuna.customer.api.dto.CustomerPointAdjustmentRequest;
import com.kizuna.customer.api.dto.CustomerPointBalanceResponse;
import com.kizuna.customer.application.CustomerPointService;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.storescope.CurrentStore;
import com.kizuna.shared.web.Replays;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 店舗 CRM からの会員ポイント照会と手動調整。
 *
 * <p>照会は顧客台帳の一部として読めるが、調整は残高を人手で動かす確定操作のため別権限（{@code PERM_POINT_ADJUST}）で仕切る。
 */
@RestController
@RequestMapping("/customers/{customerId}")
@RequiredArgsConstructor
public class CustomerPointController {

  private final CustomerPointService customerPointService;

  @GetMapping("/member-point-balance")
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_MANAGE')")
  public ResponseEntity<CustomerPointBalanceResponse> balance(
      @CurrentStore Long storeId, @PathVariable String customerId) {
    return ResponseEntity.ok(customerPointService.balance(storeId, customerId));
  }

  @PostMapping("/point-adjustments")
  @PreAuthorize("hasAuthority('PERM_POINT_ADJUST')")
  public ResponseEntity<CustomerPointBalanceResponse> adjust(
      @CurrentStore Long storeId,
      @PathVariable String customerId,
      @Valid @RequestBody CustomerPointAdjustmentRequest request,
      Principal principal) {
    return ResponseEntity.ok(
        Replays.write(
            DbConstraint.UQ_T_POINT_ENTRIES_IDEMPOTENCY_KEY,
            () -> customerPointService.adjust(storeId, customerId, request, principal.getName()),
            () ->
                customerPointService.replayAdjust(
                    storeId, customerId, request, principal.getName())));
  }
}
