package com.kizuna.customer.api.store;

import com.kizuna.customer.api.dto.ContactHistoryResponse;
import com.kizuna.customer.api.dto.ContactPreferenceRequest;
import com.kizuna.customer.api.dto.ContactRequest;
import com.kizuna.customer.api.dto.ContactResponse;
import com.kizuna.customer.application.CustomerContactService;
import com.kizuna.customer.domain.ContactType;
import com.kizuna.shared.web.CursorPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/store/customers/{customerId}")
@RequiredArgsConstructor
public class CustomerContactController {
  private final CustomerContactService service;

  @GetMapping("/contacts")
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_MANAGE')")
  public CursorPage<ContactResponse> list(
      @PathVariable String customerId,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(customerId, cursor, size);
  }

  @PostMapping("/contacts")
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_MANAGE')")
  public ResponseEntity<ContactResponse> create(
      @PathVariable String customerId, @Valid @RequestBody ContactRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(customerId, request));
  }

  @PutMapping("/contacts/{contactId}")
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_MANAGE')")
  public ContactResponse update(
      @PathVariable String customerId,
      @PathVariable String contactId,
      @Valid @RequestBody ContactRequest request) {
    return service.update(customerId, contactId, request);
  }

  @DeleteMapping("/contacts/{contactId}")
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_MANAGE')")
  public ResponseEntity<Void> delete(
      @PathVariable String customerId, @PathVariable String contactId) {
    service.delete(customerId, contactId);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/contact-preferences/{type}")
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_MANAGE')")
  public ResponseEntity<Void> prefer(
      @PathVariable String customerId,
      @PathVariable ContactType type,
      @Valid @RequestBody ContactPreferenceRequest request) {
    service.prefer(customerId, type, request.getContactId());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/contact-history")
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_MANAGE')")
  public CursorPage<ContactHistoryResponse> history(
      @PathVariable String customerId,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return service.history(customerId, cursor, size);
  }
}
