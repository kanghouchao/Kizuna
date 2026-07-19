package com.kizuna.tenant.api.platform;

import com.kizuna.tenant.api.dto.PaginatedTenantVO;
import com.kizuna.tenant.api.dto.PlatformStoreResponse;
import com.kizuna.tenant.api.dto.TenantCreateDTO;
import com.kizuna.tenant.api.dto.TenantStatusVO;
import com.kizuna.tenant.api.dto.TenantUpdateDTO;
import com.kizuna.tenant.api.dto.TenantVO;
import com.kizuna.tenant.application.CentralTenantService;
import com.kizuna.tenant.application.PlatformStoreService;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
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

/** 平台（統一）店舗 API。授権店舗一覧と中央運営の店舗 CRUD を提供する（#324 統一ログイン / #415 二命名空間）。 */
@RestController
@RequestMapping("/platform/stores")
@RequiredArgsConstructor
public class PlatformStoreController {

  private final PlatformStoreService platformStoreService;
  private final CentralTenantService tenantService;

  @GetMapping("/me")
  @PreAuthorize("hasAuthority('PERM_STORE_VIEW')")
  public ResponseEntity<List<PlatformStoreResponse>> listAuthorized() {
    return ResponseEntity.ok(platformStoreService.listAuthorizedStores());
  }

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_STORE_MANAGE')")
  public ResponseEntity<PaginatedTenantVO<TenantVO>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(name = "per_page", defaultValue = "10") int perPage,
      @RequestParam(required = false) String search) {
    return ResponseEntity.ok(tenantService.list(page, perPage, search));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_STORE_MANAGE')")
  public ResponseEntity<TenantVO> getById(@PathVariable String id) {
    return tenantService
        .getById(id)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  /**
   * 公開ドメイン照会。未認証で呼べ、frontend の middleware が店舗情報を解決するために用いる。
   *
   * @param domain 照会対象の店舗ドメイン
   * @return 該当店舗の {@link TenantVO}、存在しなければ 404
   */
  @GetMapping("/lookup")
  @PermitAll
  public ResponseEntity<TenantVO> getByDomain(@RequestParam String domain) {
    return tenantService
        .getByDomain(domain)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PostMapping
  @PreAuthorize("hasAuthority('PERM_STORE_MANAGE')")
  public ResponseEntity<Void> create(@Valid @RequestBody TenantCreateDTO tenant) {
    tenantService.create(tenant);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_STORE_MANAGE')")
  public ResponseEntity<Void> update(@PathVariable String id, @RequestBody TenantUpdateDTO tenant) {
    tenantService.update(id, tenant);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_STORE_MANAGE')")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    tenantService.delete(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/stats")
  @PreAuthorize("hasAuthority('PERM_STORE_MANAGE')")
  public ResponseEntity<TenantStatusVO> stats() {
    return ResponseEntity.ok(tenantService.stats());
  }
}
