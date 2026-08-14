package com.kizuna.customer.api.store;

import com.kizuna.customer.api.dto.CustomerMergeRequest;
import com.kizuna.customer.api.dto.CustomerMergeResponse;
import com.kizuna.customer.application.CustomerMergeService;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 顧客統合の実行。パスが名指すのが存続行で、本文が被統合行を指す。
 *
 * <p>統合を取り消す端点は無い。誤統合の修復は統合履歴を根拠とする人手作業である（ADR 0010）。
 */
@RestController
@RequestMapping("/store/customers/{customerId}/merges")
@RequiredArgsConstructor
public class CustomerMergeController {

  private final CustomerMergeService customerMergeService;

  @PostMapping
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_MERGE')")
  public ResponseEntity<CustomerMergeResponse> merge(
      @PathVariable String customerId,
      @Valid @RequestBody CustomerMergeRequest request,
      Principal principal) {
    return ResponseEntity.ok(
        customerMergeService.merge(customerId, request.getMergedCustomerId(), principal.getName()));
  }
}
