package com.kizuna.customer.api.store;

import com.kizuna.customer.api.dto.CustomerMergeHistoryResponse;
import com.kizuna.customer.api.dto.CustomerMergeRequest;
import com.kizuna.customer.api.dto.CustomerMergeResponse;
import com.kizuna.customer.application.CustomerMergeService;
import com.kizuna.shared.web.CursorPage;
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
import org.springframework.web.bind.annotation.RequestParam;
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

  /**
   * その顧客に関する統合履歴。存続行として受けた統合と、自分が被統合となった統合の両方が新しい順に返る。続きは応答の {@code next_cursor} をそのまま {@code
   * cursor} に渡して取る。
   *
   * <p>パスの意味が POST と非対称なのは意図したもの。POST の {@code customerId} は存続行を名指すが、GET
   * は「この行が関与した統合」の集合を指すので、被統合となった側も含む。
   *
   * <p>統合の実行と同じ {@code CUSTOMER_MERGE} で守る。履歴は誰がどの顧客を畳んだかを明かす機微情報で、 誤統合の修復に当たる者だけが読めばよい（ADR 0010）。
   */
  @GetMapping
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_MERGE')")
  public ResponseEntity<CursorPage<CustomerMergeHistoryResponse>> history(
      @PathVariable String customerId,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(customerMergeService.history(customerId, cursor, size));
  }
}
