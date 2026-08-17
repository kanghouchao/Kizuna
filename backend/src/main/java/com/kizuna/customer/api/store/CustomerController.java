package com.kizuna.customer.api.store;

import com.kizuna.customer.api.dto.CustomerCreateRequest;
import com.kizuna.customer.api.dto.CustomerDuplicateGroupResponse;
import com.kizuna.customer.api.dto.CustomerMergeComparisonResponse;
import com.kizuna.customer.api.dto.CustomerResponse;
import com.kizuna.customer.api.dto.CustomerSummaryResponse;
import com.kizuna.customer.api.dto.CustomerUpdateRequest;
import com.kizuna.customer.application.CustomerService;
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
@RequestMapping("/store/customers")
@RequiredArgsConstructor
public class CustomerController {

  private final CustomerService customerService;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_MANAGE')")
  public ResponseEntity<Page<CustomerSummaryResponse>> list(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String rank,
      @RequestParam(required = false) String classification,
      @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
    return ResponseEntity.ok(customerService.list(search, rank, classification, pageable));
  }

  /**
   * 重複候補（同店・生きた行・第一電話番号が一致する 2 行以上のグループ）。
   *
   * <p>統合と同じ {@code CUSTOMER_MERGE} で守る。候補の提示は統合画面の一部で単独の価値を持たず、重複の在り処は機微情報だからである。
   *
   * <p>続きは応答の {@code next_cursor} をそのまま {@code cursor} に渡して取る。
   *
   * <p>字面セグメントは {@code /{id}} の値空間を恒久的に侵食する。顧客 ID はアプリ生成の数字列なので {@code duplicates} と衝突しえないが、 ID
   * の採番規則を変えるときはこの端点が先に隠れることを思い出すこと。
   */
  @GetMapping("/duplicates")
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_MERGE')")
  public ResponseEntity<CursorPage<CustomerDuplicateGroupResponse>> listDuplicates(
      @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(customerService.listDuplicateCandidates(cursor, size));
  }

  /**
   * 統合の前に見比べる 2 行。顧客一覧で選んだ任意の 2 件を、重複候補と同じ材料（受注件数・会員紐づけの有無・住所などの識別材料）つきで返す。
   *
   * <p>詳細の読み口では代わりにならない。あちらは旧 ID を統合先へ解決するので、片方が既に墓標なら同じ 1 行が 2 列に並ぶ。
   *
   * <p>統合と同じ {@code CUSTOMER_MERGE} で守る。読めるのは台帳の内部評価（NG・区分・住所）を含む見比べ材料で、 統合に当たる者だけが読めばよい。
   */
  @GetMapping("/merge-comparison")
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_MERGE')")
  public ResponseEntity<List<CustomerMergeComparisonResponse>> mergeComparison(
      @RequestParam List<String> ids) {
    return ResponseEntity.ok(customerService.mergeComparison(ids));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_MANAGE')")
  public ResponseEntity<CustomerResponse> get(@PathVariable String id) {
    return ResponseEntity.ok(customerService.get(id));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_MANAGE')")
  public ResponseEntity<CustomerResponse> create(
      @Valid @RequestBody CustomerCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(customerService.create(request));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_MANAGE')")
  public ResponseEntity<CustomerResponse> update(
      @PathVariable String id, @Valid @RequestBody CustomerUpdateRequest request) {
    return ResponseEntity.ok(customerService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_MANAGE')")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    customerService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
