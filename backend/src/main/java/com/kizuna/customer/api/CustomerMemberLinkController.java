package com.kizuna.customer.api;

import com.kizuna.customer.api.dto.CustomerMemberLinkHistoryResponse;
import com.kizuna.customer.api.dto.CustomerMemberLinkReleaseRequest;
import com.kizuna.customer.api.dto.CustomerMemberLinkRequest;
import com.kizuna.customer.api.dto.CustomerMemberLinkResponse;
import com.kizuna.customer.application.CustomerMemberLinkService;
import com.kizuna.shared.storescope.CurrentStore;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customers/{customerId}/member-link")
@RequiredArgsConstructor
public class CustomerMemberLinkController {

  private final CustomerMemberLinkService customerMemberLinkService;

  @PostMapping
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_MANAGE')")
  public ResponseEntity<CustomerMemberLinkResponse> link(
      @CurrentStore Long storeId,
      @PathVariable String customerId,
      @Valid @RequestBody CustomerMemberLinkRequest request,
      Principal principal) {
    return ResponseEntity.status(201)
        .body(
            customerMemberLinkService.link(
                storeId,
                customerId,
                request.getMemberCode(),
                request.getExpectedLinkId(),
                request.getOperationReason(),
                principal.getName()));
  }

  @PostMapping("/releases")
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_MANAGE')")
  public ResponseEntity<Void> unlink(
      @CurrentStore Long storeId,
      @PathVariable String customerId,
      @Valid @RequestBody CustomerMemberLinkReleaseRequest request,
      Principal principal) {
    customerMemberLinkService.unlink(
        storeId,
        customerId,
        request.expectedLinkId(),
        request.operationReason(),
        principal.getName());
    return ResponseEntity.noContent().build();
  }

  /** 現に有効な紐づけ。紐づいていない顧客では 404 で返る。 */
  @GetMapping
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_MANAGE')")
  public ResponseEntity<CustomerMemberLinkResponse> current(
      @CurrentStore Long storeId, @PathVariable String customerId) {
    return ResponseEntity.ok(customerMemberLinkService.current(storeId, customerId));
  }

  @GetMapping("/history")
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_MANAGE')")
  public ResponseEntity<Page<CustomerMemberLinkHistoryResponse>> history(
      @CurrentStore Long storeId,
      @PathVariable String customerId,
      @PageableDefault(
              size = 20,
              sort = {"linkedAt", "id"},
              direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(customerMemberLinkService.history(storeId, customerId, pageable));
  }
}
