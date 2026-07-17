package com.kizuna.shift.api.store;

import com.kizuna.shift.api.dto.PublicShiftResponse;
import com.kizuna.shift.api.dto.ShiftCreateRequest;
import com.kizuna.shift.api.dto.ShiftResponse;
import com.kizuna.shift.api.dto.ShiftUpdateRequest;
import com.kizuna.shift.application.ShiftService;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/tenant/shifts")
@RequiredArgsConstructor
public class ShiftController {

  private final ShiftService shiftService;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_SHIFT_MANAGE')")
  public ResponseEntity<List<ShiftResponse>> list(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return ResponseEntity.ok(shiftService.list(from, to));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('PERM_SHIFT_MANAGE')")
  public ResponseEntity<ShiftResponse> create(@Valid @RequestBody ShiftCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(shiftService.create(request));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_SHIFT_MANAGE')")
  public ResponseEntity<ShiftResponse> update(
      @PathVariable String id, @Valid @RequestBody ShiftUpdateRequest request) {
    return ResponseEntity.ok(shiftService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_SHIFT_MANAGE')")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    shiftService.delete(id);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/public")
  @PermitAll
  public ResponseEntity<List<PublicShiftResponse>> listPublic() {
    return ResponseEntity.ok(shiftService.listPublicToday());
  }
}
