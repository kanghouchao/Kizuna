package com.kizuna.shift.api;

import com.kizuna.shared.storescope.CurrentStore;
import com.kizuna.shift.api.dto.PublicShiftResponse;
import com.kizuna.shift.api.dto.ShiftCreateRequest;
import com.kizuna.shift.api.dto.ShiftDetailResponse;
import com.kizuna.shift.api.dto.ShiftPublicationRequest;
import com.kizuna.shift.api.dto.ShiftRequestLineageResponse;
import com.kizuna.shift.api.dto.ShiftResponse;
import com.kizuna.shift.api.dto.ShiftUpdateRequest;
import com.kizuna.shift.application.ShiftLineageService;
import com.kizuna.shift.application.ShiftService;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/shifts")
@RequiredArgsConstructor
public class ShiftController {

  private final ShiftService shiftService;
  private final ShiftLineageService shiftLineageService;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_SHIFT_MANAGE')")
  public ResponseEntity<List<ShiftResponse>> list(
      @CurrentStore Long storeId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return ResponseEntity.ok(shiftService.list(storeId, from, to));
  }

  /** 1 本のシフトを系列ごと返す。字面の {@code /public} とは値空間が交わらない（シフト id は数字だけの Snowflake）。 */
  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_SHIFT_MANAGE')")
  public ResponseEntity<ShiftDetailResponse> detail(
      @CurrentStore Long storeId, @PathVariable String id) {
    return ResponseEntity.ok(shiftLineageService.detail(storeId, id));
  }

  /**
   * シフトの変更申請履歴。詳細に埋めないのは、提出のたびに増え続けて有界でないためである （backend/AGENTS.md の Pagination）。出生の NEW は高々 1
   * 本なので詳細側にある。
   */
  @GetMapping("/{id}/change-requests")
  @PreAuthorize("hasAuthority('PERM_SHIFT_MANAGE')")
  public ResponseEntity<Page<ShiftRequestLineageResponse>> changeRequests(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @PageableDefault(
              size = 50,
              sort = {"createdAt", "id"},
              direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(shiftLineageService.changeRequests(storeId, id, pageable));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('PERM_SHIFT_MANAGE')")
  public ResponseEntity<ShiftResponse> create(
      @CurrentStore Long storeId,
      @Valid @RequestBody ShiftCreateRequest request,
      Principal principal) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(shiftService.create(storeId, request, principal.getName()));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_SHIFT_MANAGE')")
  public ResponseEntity<ShiftResponse> update(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @Valid @RequestBody ShiftUpdateRequest request,
      Principal principal) {
    return ResponseEntity.ok(shiftService.update(storeId, id, request, principal.getName()));
  }

  @PutMapping("/{id}/publication")
  @PreAuthorize("hasAuthority('PERM_SHIFT_MANAGE')")
  public ResponseEntity<ShiftResponse> changePublication(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @Valid @RequestBody ShiftPublicationRequest request,
      Principal principal) {
    return ResponseEntity.ok(
        shiftService.changePublication(storeId, id, request.getPublished(), principal.getName()));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_SHIFT_MANAGE')")
  public ResponseEntity<Void> delete(@CurrentStore Long storeId, @PathVariable String id) {
    shiftService.delete(storeId, id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/public")
  @PermitAll
  public ResponseEntity<List<PublicShiftResponse>> listPublic(@CurrentStore Long storeId) {
    return ResponseEntity.ok(shiftService.listPublicToday(storeId));
  }
}
