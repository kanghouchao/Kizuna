package com.kizuna.customer.api.store;

import com.kizuna.customer.api.dto.CustomerCreateRequest;
import com.kizuna.customer.api.dto.CustomerDuplicateCandidatesResponse;
import com.kizuna.customer.api.dto.CustomerResponse;
import com.kizuna.customer.api.dto.CustomerSummaryResponse;
import com.kizuna.customer.api.dto.CustomerUpdateRequest;
import com.kizuna.customer.application.CustomerService;
import jakarta.validation.Valid;
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
   * <p>字面セグメントは {@code /{id}} の値空間を恒久的に侵食する。顧客 ID はアプリ生成の数字列なので {@code duplicates} と衝突しえないが、 ID
   * の採番規則を変えるときはこの端点が先に隠れることを思い出すこと。
   */
  @GetMapping("/duplicates")
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_MERGE')")
  public ResponseEntity<CustomerDuplicateCandidatesResponse> listDuplicates() {
    return ResponseEntity.ok(customerService.listDuplicateCandidates());
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
