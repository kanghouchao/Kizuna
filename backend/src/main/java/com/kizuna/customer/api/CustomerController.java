package com.kizuna.customer.api;

import com.kizuna.customer.api.dto.CustomerCreateRequest;
import com.kizuna.customer.api.dto.CustomerDuplicateGroupResponse;
import com.kizuna.customer.api.dto.CustomerMergeComparisonResponse;
import com.kizuna.customer.api.dto.CustomerResponse;
import com.kizuna.customer.api.dto.CustomerSummaryResponse;
import com.kizuna.customer.api.dto.CustomerUpdateRequest;
import com.kizuna.customer.application.CustomerService;
import com.kizuna.customer.domain.ContactType;
import com.kizuna.shared.storescope.CurrentStore;
import com.kizuna.shared.web.CursorPage;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController {

  private final CustomerService customerService;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_MANAGE')")
  public ResponseEntity<Page<CustomerSummaryResponse>> list(
      @CurrentStore Long storeId,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String classification,
      @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
    return ResponseEntity.ok(customerService.list(storeId, search, classification, pageable));
  }

  /** 同店の未統合顧客が共有する有効連絡先を、種類・値の順で提示する。 */
  @GetMapping("/duplicates")
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_MERGE') and hasAuthority('PERM_CUSTOMER_MANAGE')")
  public ResponseEntity<CursorPage<CustomerDuplicateGroupResponse>> listDuplicates(
      @CurrentStore Long storeId,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) ContactType type,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(
        customerService.listDuplicateCandidates(storeId, search, type, cursor, size));
  }

  @GetMapping("/duplicates/customers")
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_MERGE') and hasAuthority('PERM_CUSTOMER_MANAGE')")
  public ResponseEntity<CursorPage<CustomerMergeComparisonResponse>> duplicateCustomers(
      @CurrentStore Long storeId,
      @RequestParam ContactType type,
      @RequestParam String value,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(
        customerService.duplicateCustomers(storeId, type, value, cursor, size));
  }

  /**
   * 統合の前に見比べる 2 行。顧客一覧で選んだ任意の 2 件を、重複候補と同じ材料つきで返す。
   *
   * <p>統合と同じ {@code CUSTOMER_MERGE} で守る。返すのは NG・区分・住所を含む台帳の内部評価で、統合に当たる者だけが読めばよい。
   */
  @GetMapping("/merge-comparison")
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_MERGE') and hasAuthority('PERM_CUSTOMER_MANAGE')")
  public ResponseEntity<List<CustomerMergeComparisonResponse>> mergeComparison(
      @CurrentStore Long storeId, @RequestParam List<String> ids) {
    return ResponseEntity.ok(customerService.mergeComparison(storeId, ids));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_MANAGE')")
  public ResponseEntity<CustomerResponse> get(@CurrentStore Long storeId, @PathVariable String id) {
    return ResponseEntity.ok(customerService.get(storeId, id));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_MANAGE')")
  public ResponseEntity<CustomerResponse> create(
      @CurrentStore Long storeId, @Valid @RequestBody CustomerCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(customerService.create(storeId, request));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_MANAGE')")
  public ResponseEntity<CustomerResponse> update(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @Valid @RequestBody CustomerUpdateRequest request) {
    return ResponseEntity.ok(customerService.update(storeId, id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_MANAGE')")
  public ResponseEntity<Void> delete(@CurrentStore Long storeId, @PathVariable String id) {
    customerService.delete(storeId, id);
    return ResponseEntity.noContent().build();
  }
}
