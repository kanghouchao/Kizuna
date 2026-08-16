package com.kizuna.customer.api.store;

import com.kizuna.customer.api.dto.CustomerMemberLinkHistoryResponse;
import com.kizuna.customer.api.dto.CustomerMemberLinkRequest;
import com.kizuna.customer.api.dto.CustomerMemberLinkResponse;
import com.kizuna.customer.application.CustomerMemberLinkService;
import com.kizuna.shared.web.CursorPage;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/store/customers/{customerId}/member-link")
@RequiredArgsConstructor
public class CustomerMemberLinkController {

  private final CustomerMemberLinkService customerMemberLinkService;

  @PostMapping
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_MANAGE')")
  public ResponseEntity<CustomerMemberLinkResponse> link(
      @PathVariable String customerId,
      @Valid @RequestBody CustomerMemberLinkRequest request,
      Principal principal) {
    return ResponseEntity.ok(
        customerMemberLinkService.link(customerId, request.getMemberCode(), principal.getName()));
  }

  @DeleteMapping
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_MANAGE')")
  public ResponseEntity<Void> unlink(@PathVariable String customerId, Principal principal) {
    customerMemberLinkService.unlink(customerId, principal.getName());
    return ResponseEntity.ok().build();
  }

  /** 現に有効な紐づけ。紐づいていない顧客では 404 で返る。 */
  @GetMapping
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_MANAGE')")
  public ResponseEntity<CustomerMemberLinkResponse> current(@PathVariable String customerId) {
    return ResponseEntity.ok(customerMemberLinkService.current(customerId));
  }

  /** 紐づけ履歴。続きは応答の {@code next_cursor} をそのまま {@code cursor} に渡して取る。 */
  @GetMapping("/history")
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_MANAGE')")
  public ResponseEntity<CursorPage<CustomerMemberLinkHistoryResponse>> history(
      @PathVariable String customerId,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(customerMemberLinkService.history(customerId, cursor, size));
  }
}
