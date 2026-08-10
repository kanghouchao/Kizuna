package com.kizuna.customer.api.store;

import com.kizuna.customer.api.dto.CustomerPointAdjustmentRequest;
import com.kizuna.customer.api.dto.CustomerPointBalanceResponse;
import com.kizuna.customer.application.CustomerPointService;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.IntegrityViolations;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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

  /**
   * 同時再送で冪等キーの一意制約に敗れた場合だけ、初回の結果を読み直す再送処理へ回す（ADR 0007）。
   *
   * <p>この分岐はサービスのトランザクション境界の外に置かなければならない — 制約違反の時点で敗者のトランザクションは 作廃されており、内側で catch
   * しても読み直せない。他の整合性違反は実装欠陥なのでそのまま上げ、全域ハンドラの分類に委ねる。
   */
  @PostMapping("/point-adjustments")
  @PreAuthorize("hasAuthority('PERM_POINT_ADJUST')")
  public ResponseEntity<CustomerPointBalanceResponse> adjust(
      @PathVariable String customerId,
      @Valid @RequestBody CustomerPointAdjustmentRequest request,
      Principal principal) {
    try {
      return ResponseEntity.ok(
          customerPointService.adjust(customerId, request, principal.getName()));
    } catch (DataIntegrityViolationException ex) {
      if (!IntegrityViolations.violates(ex, DbConstraint.UQ_T_POINT_ENTRIES_IDEMPOTENCY_KEY)) {
        throw ex;
      }
      return ResponseEntity.ok(
          customerPointService.replayAdjust(customerId, request, principal.getName()));
    }
  }
}
