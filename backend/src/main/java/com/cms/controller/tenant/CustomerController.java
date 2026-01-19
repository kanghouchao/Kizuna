package com.cms.controller.tenant;

import com.cms.model.dto.tenant.customer.CustomerCreateRequest;
import com.cms.model.dto.tenant.customer.CustomerResponse;
import com.cms.model.dto.tenant.customer.CustomerUpdateRequest;
import com.cms.service.tenant.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/tenant/customers")
@RequiredArgsConstructor
public class CustomerController {

  private final CustomerService customerService;

  @GetMapping
  public ResponseEntity<Page<CustomerResponse>> list(
      @RequestParam(required = false) String search,
      @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
    return ResponseEntity.ok(customerService.list(search, pageable));
  }

  @GetMapping("/{id}")
  public ResponseEntity<CustomerResponse> get(@PathVariable String id) {
    return ResponseEntity.ok(customerService.get(id));
  }

  @PostMapping
  public ResponseEntity<CustomerResponse> create(
      @Valid @RequestBody CustomerCreateRequest request) {
    return ResponseEntity.ok(customerService.create(request));
  }

  @PutMapping("/{id}")
  public ResponseEntity<CustomerResponse> update(
      @PathVariable String id, @Valid @RequestBody CustomerUpdateRequest request) {
    return ResponseEntity.ok(customerService.update(id, request));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    customerService.delete(id);
    return ResponseEntity.ok().build();
  }
}
