package com.kizuna.remuneration.api.store;

import com.kizuna.remuneration.api.dto.GuaranteeListResponse;
import com.kizuna.remuneration.api.dto.GuaranteeMutationResponse;
import com.kizuna.remuneration.api.dto.RemunerationChangeResponse;
import com.kizuna.remuneration.api.dto.RemunerationRequests.CancellationRequest;
import com.kizuna.remuneration.api.dto.RemunerationRequests.GuaranteeCorrectionRequest;
import com.kizuna.remuneration.api.dto.RemunerationRequests.GuaranteeCreateRequest;
import com.kizuna.remuneration.application.RemunerationManagementService;
import com.kizuna.shared.web.CursorPage;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/store/remuneration-guarantees")
@RequiredArgsConstructor
public class GuaranteeController {
  private final RemunerationManagementService service;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE') and hasAuthority('PERM_REMUNERATION_VIEW')")
  public GuaranteeListResponse list(
      @RequestParam(name = "person_id") Long personId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.guarantees(personId, page, size);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize(
      "hasAuthority('PERM_ORDER_MANAGE') and hasAuthority('PERM_REMUNERATION_VIEW') and hasAuthority('PERM_GUARANTEE_MANAGE')")
  public GuaranteeMutationResponse create(
      @Valid @RequestBody GuaranteeCreateRequest request, Principal principal) {
    return service.createGuarantee(request, principal.getName());
  }

  @PostMapping("/{id}/corrections")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize(
      "hasAuthority('PERM_ORDER_MANAGE') and hasAuthority('PERM_REMUNERATION_VIEW') and hasAuthority('PERM_GUARANTEE_MANAGE') and hasAuthority('PERM_REMUNERATION_CORRECT')")
  public GuaranteeMutationResponse correct(
      @PathVariable String id,
      @Valid @RequestBody GuaranteeCorrectionRequest request,
      Principal principal) {
    return service.correctGuarantee(id, request, principal.getName());
  }

  @PostMapping("/{id}/cancellation")
  @PreAuthorize(
      "hasAuthority('PERM_ORDER_MANAGE') and hasAuthority('PERM_REMUNERATION_VIEW') and hasAuthority('PERM_GUARANTEE_MANAGE') and hasAuthority('PERM_REMUNERATION_CORRECT')")
  public GuaranteeMutationResponse cancel(
      @PathVariable String id,
      @Valid @RequestBody CancellationRequest request,
      Principal principal) {
    return service.cancelGuarantee(id, request, principal.getName());
  }

  @GetMapping("/{id}/changes")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE') and hasAuthority('PERM_REMUNERATION_VIEW')")
  public CursorPage<RemunerationChangeResponse> history(
      @PathVariable String id,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return service.guaranteeChanges(id, cursor, size);
  }
}
