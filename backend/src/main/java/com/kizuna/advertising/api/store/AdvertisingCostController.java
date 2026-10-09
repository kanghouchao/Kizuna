package com.kizuna.advertising.api.store;

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
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.web.CursorPage;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/store")
@RequiredArgsConstructor
public class AdvertisingCostController {
  private final AdvertisingService service;
  private final AdvertisingExportService exports;
  private final StoreContext stores;

  @GetMapping("/advertising-costs")
  @PreAuthorize("hasAuthority('PERM_ADVERTISING_COST_VIEW')")
  public ResponseEntity<Page<CostSummary>> list(
      @RequestParam String month,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(AdvertisingFailures.run(() -> service.list(month, page, size)));
  }

  @GetMapping("/advertising-costs/{id}")
  @PreAuthorize("hasAuthority('PERM_ADVERTISING_COST_VIEW')")
  public ResponseEntity<CostResponse> get(@PathVariable String id) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(id));
  }

  @PostMapping("/advertising-costs")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize(
      "hasAuthority('PERM_ADVERTISING_COST_VIEW') and hasAuthority('PERM_ADVERTISING_COST_MANAGE')")
  public CostResponse create(@Valid @RequestBody CreateRequest request, Principal actor) {
    return AdvertisingFailures.run(() -> service.create(request, actor.getName()));
  }

  @PutMapping("/advertising-costs/{id}")
  @PreAuthorize(
      "hasAuthority('PERM_ADVERTISING_COST_VIEW') and hasAuthority('PERM_ADVERTISING_COST_MANAGE')")
  public CostResponse replace(
      @PathVariable String id, @Valid @RequestBody ReplaceRequest request, Principal actor) {
    return AdvertisingFailures.run(() -> service.replace(id, request, actor.getName()));
  }

  @DeleteMapping("/advertising-costs/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize(
      "hasAuthority('PERM_ADVERTISING_COST_VIEW') and hasAuthority('PERM_ADVERTISING_COST_MANAGE')")
  public void delete(
      @PathVariable String id, @Valid @RequestBody DeleteRequest request, Principal actor) {
    AdvertisingFailures.run(
        () -> {
          service.delete(id, request, actor.getName());
          return true;
        });
  }

  @GetMapping("/advertising-cost-months/{month}")
  @PreAuthorize("hasAuthority('PERM_ADVERTISING_COST_VIEW')")
  public ResponseEntity<MonthResponse> month(@PathVariable String month) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(AdvertisingFailures.run(() -> service.month(month)));
  }

  @PostMapping("/advertising-cost-months/{month}/copies")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize(
      "hasAuthority('PERM_ADVERTISING_COST_VIEW') and hasAuthority('PERM_ADVERTISING_COST_MANAGE')")
  public CopyResponse copy(
      @PathVariable String month, @Valid @RequestBody CopyRequest request, Principal actor) {
    return AdvertisingFailures.run(() -> service.copy(month, request, actor.getName()));
  }

  @GetMapping("/advertising-cost-months/{month}/changes")
  @PreAuthorize("hasAuthority('PERM_ADVERTISING_COST_VIEW')")
  public ResponseEntity<CursorPage<ChangeSummary>> changes(
      @PathVariable String month,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(service.changes(month, cursor, size));
  }

  @GetMapping("/advertising-cost-months/{month}/changes/{id}")
  @PreAuthorize("hasAuthority('PERM_ADVERTISING_COST_VIEW')")
  public ResponseEntity<ChangeResponse> change(
      @PathVariable String month, @PathVariable String id) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(service.changeDetail(month, id));
  }

  @GetMapping("/advertising-costs/exports")
  @PreAuthorize(
      "hasAuthority('PERM_ADVERTISING_COST_VIEW') and hasAuthority('PERM_ADVERTISING_COST_EXPORT')")
  public ResponseEntity<byte[]> export(@RequestParam String month, @RequestParam String format) {
    var bytes = exports.export(month, format);
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
                + stores.getStoreId()
                + "-"
                + month
                + "."
                + format
                + "\"")
        .body(bytes);
  }
}
