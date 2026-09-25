package com.kizuna.customer.api.store;

import com.kizuna.customer.api.dto.CustomerMergePreviewRequest;
import com.kizuna.customer.api.dto.CustomerMergePreviewResponse;
import com.kizuna.customer.application.CustomerMergeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CustomerMergePreviewController {
  private final CustomerMergeService service;

  @PostMapping("/store/customers/{customerId}/merge-preview")
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_MERGE') and hasAuthority('PERM_CUSTOMER_MANAGE')")
  public CustomerMergePreviewResponse preview(
      @PathVariable String customerId, @Valid @RequestBody CustomerMergePreviewRequest request) {
    return service.preview(customerId, request);
  }
}
