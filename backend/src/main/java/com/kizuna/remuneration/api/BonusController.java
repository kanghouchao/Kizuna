package com.kizuna.remuneration.api;

import com.kizuna.remuneration.api.dto.BonusMutationResponse;
import com.kizuna.remuneration.api.dto.BonusResponse;
import com.kizuna.remuneration.api.dto.RemunerationChangeResponse;
import com.kizuna.remuneration.api.dto.RemunerationRequests.BonusCorrectionRequest;
import com.kizuna.remuneration.api.dto.RemunerationRequests.BonusCreateRequest;
import com.kizuna.remuneration.api.dto.RemunerationRequests.CancellationRequest;
import com.kizuna.remuneration.application.RemunerationManagementService;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.storescope.CurrentStore;
import com.kizuna.shared.web.Replays;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
@RequestMapping("/bonus-awards")
@RequiredArgsConstructor
public class BonusController {
  private final RemunerationManagementService service;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE') and hasAuthority('PERM_REMUNERATION_VIEW')")
  public Page<BonusResponse> list(
      @CurrentStore Long storeId,
      @RequestParam(name = "person_id") Long personId,
      @RequestParam String month,
      @PageableDefault(
              size = 20,
              sort = {})
          Pageable pageable) {
    return service.bonuses(storeId, personId, month, pageable).map(BonusResponse::of);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize(
      "hasAuthority('PERM_ORDER_MANAGE') and hasAuthority('PERM_REMUNERATION_VIEW') and hasAuthority('PERM_BONUS_AWARD')")
  public BonusMutationResponse create(
      @CurrentStore Long storeId,
      @Valid @RequestBody BonusCreateRequest request,
      Principal principal) {
    var command = request.command();
    return BonusMutationResponse.of(
        Replays.write(
            DbConstraint.UQ_T_OPERATION_RECEIPTS_KEY,
            () -> service.createBonus(storeId, command, principal.getName()),
            () -> service.replayBonus(storeId, command, principal.getName())));
  }

  @PostMapping("/{id}/corrections")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize(
      "hasAuthority('PERM_ORDER_MANAGE') and hasAuthority('PERM_REMUNERATION_VIEW') and hasAuthority('PERM_BONUS_AWARD') and hasAuthority('PERM_REMUNERATION_CORRECT')")
  public BonusMutationResponse correct(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @Valid @RequestBody BonusCorrectionRequest request,
      Principal principal) {
    var command = request.command(id);
    return BonusMutationResponse.of(
        Replays.write(
            DbConstraint.UQ_T_OPERATION_RECEIPTS_KEY,
            () -> service.correctBonus(storeId, command, principal.getName()),
            () -> service.replayBonus(storeId, command, principal.getName())));
  }

  @PostMapping("/{id}/cancellation")
  @PreAuthorize(
      "hasAuthority('PERM_ORDER_MANAGE') and hasAuthority('PERM_REMUNERATION_VIEW') and hasAuthority('PERM_BONUS_AWARD') and hasAuthority('PERM_REMUNERATION_CORRECT')")
  public BonusMutationResponse cancel(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @Valid @RequestBody CancellationRequest request,
      Principal principal) {
    var command = request.bonusCommand(id);
    return BonusMutationResponse.of(
        Replays.write(
            DbConstraint.UQ_T_OPERATION_RECEIPTS_KEY,
            () -> service.cancelBonus(storeId, command, principal.getName()),
            () -> service.replayBonus(storeId, command, principal.getName())));
  }

  @GetMapping("/{id}/changes")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE') and hasAuthority('PERM_REMUNERATION_VIEW')")
  public Page<RemunerationChangeResponse> history(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @PageableDefault(
              size = 20,
              sort = {"createdAt", "id"},
              direction = Sort.Direction.DESC)
          Pageable pageable) {
    return service.bonusChanges(storeId, id, pageable).map(RemunerationChangeResponse::of);
  }
}
