package com.kizuna.customer.api.store;

import com.kizuna.customer.api.dto.CustomerPointAdjustmentRequest;
import com.kizuna.customer.api.dto.CustomerPointBalanceResponse;
import com.kizuna.customer.application.CustomerPointService;
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
@RequestMapping("/store/customers/{customerId}")
@RequiredArgsConstructor
public class CustomerPointController {

  private final CustomerPointService customerPointService;

  @GetMapping("/member-point-balance")
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_MANAGE')")
  public ResponseEntity<CustomerPointBalanceResponse> balance(@PathVariable String customerId) {
    return ResponseEntity.ok(customerPointService.balance(customerId));
  }

  @PostMapping("/point-adjustments")
  @PreAuthorize("hasAuthority('PERM_POINT_ADJUST')")
  public ResponseEntity<CustomerPointBalanceResponse> adjust(
      @PathVariable String customerId,
      @Valid @RequestBody CustomerPointAdjustmentRequest request,
      Principal principal) {
    return ResponseEntity.ok(customerPointService.adjust(customerId, request, principal.getName()));
  }
}
