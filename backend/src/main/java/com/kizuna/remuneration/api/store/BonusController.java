package com.kizuna.remuneration.api.store;

import com.kizuna.remuneration.api.dto.BonusMutationResponse;
import com.kizuna.remuneration.api.dto.BonusResponse;
import com.kizuna.remuneration.api.dto.RemunerationChangeResponse;
import com.kizuna.remuneration.api.dto.RemunerationRequests.BonusCorrectionRequest;
import com.kizuna.remuneration.api.dto.RemunerationRequests.BonusCreateRequest;
import com.kizuna.remuneration.api.dto.RemunerationRequests.CancellationRequest;
import com.kizuna.remuneration.application.RemunerationManagementService;
import com.kizuna.shared.web.CursorPage;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
@RequestMapping("/store/bonus-awards")
@RequiredArgsConstructor
public class BonusController {
  private final RemunerationManagementService service;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE') and hasAuthority('PERM_REMUNERATION_VIEW')")
  public Page<BonusResponse> list(
      @RequestParam(name = "person_id") Long personId,
      @RequestParam String month,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.bonuses(personId, month, page, size);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize(
      "hasAuthority('PERM_ORDER_MANAGE') and hasAuthority('PERM_REMUNERATION_VIEW') and hasAuthority('PERM_BONUS_AWARD')")
  public BonusMutationResponse create(
      @Valid @RequestBody BonusCreateRequest request, Principal principal) {
    return service.createBonus(request, principal.getName());
  }

  @PostMapping("/{id}/corrections")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize(
      "hasAuthority('PERM_ORDER_MANAGE') and hasAuthority('PERM_REMUNERATION_VIEW') and hasAuthority('PERM_BONUS_AWARD') and hasAuthority('PERM_REMUNERATION_CORRECT')")
  public BonusMutationResponse correct(
      @PathVariable String id,
      @Valid @RequestBody BonusCorrectionRequest request,
      Principal principal) {
    return service.correctBonus(id, request, principal.getName());
  }

  @PostMapping("/{id}/cancellation")
  @PreAuthorize(
      "hasAuthority('PERM_ORDER_MANAGE') and hasAuthority('PERM_REMUNERATION_VIEW') and hasAuthority('PERM_BONUS_AWARD') and hasAuthority('PERM_REMUNERATION_CORRECT')")
  public BonusMutationResponse cancel(
      @PathVariable String id,
      @Valid @RequestBody CancellationRequest request,
      Principal principal) {
    return service.cancelBonus(id, request, principal.getName());
  }

  @GetMapping("/{id}/changes")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE') and hasAuthority('PERM_REMUNERATION_VIEW')")
  public CursorPage<RemunerationChangeResponse> history(
      @PathVariable String id,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return service.bonusChanges(id, cursor, size);
  }
}
