package com.kizuna.advertising.api;

import com.kizuna.advertising.api.dto.AdvertisingRequests.CopyRequest;
import com.kizuna.advertising.api.dto.AdvertisingRequests.CreateRequest;
import com.kizuna.advertising.api.dto.AdvertisingRequests.DeleteRequest;
import com.kizuna.advertising.api.dto.AdvertisingRequests.ReplaceRequest;
import com.kizuna.advertising.api.dto.AdvertisingResponses.ChangeResponse;
import com.kizuna.advertising.api.dto.AdvertisingResponses.ChangeSummary;
import com.kizuna.advertising.api.dto.AdvertisingResponses.CopyResponse;
import com.kizuna.advertising.api.dto.AdvertisingResponses.CostResponse;
import com.kizuna.advertising.api.dto.AdvertisingResponses.CostSummary;
import com.kizuna.advertising.api.dto.AdvertisingResponses.MonthResponse;
import com.kizuna.advertising.application.AdvertisingExportService;
import com.kizuna.advertising.application.AdvertisingFailures;
import com.kizuna.advertising.application.AdvertisingService;
import com.kizuna.shared.storescope.CurrentStore;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdvertisingCostController {
  private final AdvertisingService service;
  private final AdvertisingExportService exports;

  @GetMapping("/advertising-costs")
  @PreAuthorize("hasAuthority('PERM_ADVERTISING_COST_VIEW')")
  public ResponseEntity<Page<CostSummary>> list(
      @CurrentStore Long storeId,
      @RequestParam String month,
      @PageableDefault(
              size = 20,
              sort = {})
          Pageable pageable) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(AdvertisingFailures.run(() -> service.list(storeId, month, pageable)));
  }

  @GetMapping("/advertising-costs/{id}")
  @PreAuthorize("hasAuthority('PERM_ADVERTISING_COST_VIEW')")
  public ResponseEntity<CostResponse> get(@CurrentStore Long storeId, @PathVariable String id) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(storeId, id));
  }

  @PostMapping("/advertising-costs")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize(
      "hasAuthority('PERM_ADVERTISING_COST_VIEW') and hasAuthority('PERM_ADVERTISING_COST_MANAGE')")
  public CostResponse create(
      @CurrentStore Long storeId, @Valid @RequestBody CreateRequest request, Principal actor) {
    return AdvertisingFailures.run(() -> service.create(storeId, request, actor.getName()));
  }

  @PutMapping("/advertising-costs/{id}")
  @PreAuthorize(
      "hasAuthority('PERM_ADVERTISING_COST_VIEW') and hasAuthority('PERM_ADVERTISING_COST_MANAGE')")
  public CostResponse replace(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @Valid @RequestBody ReplaceRequest request,
      Principal actor) {
    return AdvertisingFailures.run(() -> service.replace(storeId, id, request, actor.getName()));
  }

  @DeleteMapping("/advertising-costs/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize(
      "hasAuthority('PERM_ADVERTISING_COST_VIEW') and hasAuthority('PERM_ADVERTISING_COST_MANAGE')")
  public void delete(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @Valid @RequestBody DeleteRequest request,
      Principal actor) {
    AdvertisingFailures.run(
        () -> {
          service.delete(storeId, id, request, actor.getName());
          return true;
        });
  }

  @GetMapping("/advertising-cost-months/{month}")
  @PreAuthorize("hasAuthority('PERM_ADVERTISING_COST_VIEW')")
  public ResponseEntity<MonthResponse> month(
      @CurrentStore Long storeId, @PathVariable String month) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(AdvertisingFailures.run(() -> service.month(storeId, month)));
  }

  @PostMapping("/advertising-cost-months/{month}/copies")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize(
      "hasAuthority('PERM_ADVERTISING_COST_VIEW') and hasAuthority('PERM_ADVERTISING_COST_MANAGE')")
  public CopyResponse copy(
      @CurrentStore Long storeId,
      @PathVariable String month,
      @Valid @RequestBody CopyRequest request,
      Principal actor) {
    return AdvertisingFailures.run(() -> service.copy(storeId, month, request, actor.getName()));
  }

  @GetMapping("/advertising-cost-months/{month}/changes")
  @PreAuthorize("hasAuthority('PERM_ADVERTISING_COST_VIEW')")
  public ResponseEntity<Page<ChangeSummary>> changes(
      @CurrentStore Long storeId,
      @PathVariable String month,
      @PageableDefault(
              size = 20,
              sort = {"createdAt", "id"},
              direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(service.changes(storeId, month, pageable));
  }

  @GetMapping("/advertising-cost-months/{month}/changes/{id}")
  @PreAuthorize("hasAuthority('PERM_ADVERTISING_COST_VIEW')")
  public ResponseEntity<ChangeResponse> change(
      @CurrentStore Long storeId, @PathVariable String month, @PathVariable String id) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(service.changeDetail(storeId, month, id));
  }

  @GetMapping("/advertising-costs/exports")
  @PreAuthorize(
      "hasAuthority('PERM_ADVERTISING_COST_VIEW') and hasAuthority('PERM_ADVERTISING_COST_EXPORT')")
  public ResponseEntity<byte[]> export(
      @CurrentStore Long storeId, @RequestParam String month, @RequestParam String format) {
    var bytes = exports.export(storeId, month, format);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .contentType(
            MediaType.parseMediaType(
                format.equals("csv")
                    ? "text/csv;charset=UTF-8"
                    : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"advertising-costs-"
                + storeId
                + "-"
                + month
                + "."
                + format
                + "\"")
        .body(bytes);
  }
}
