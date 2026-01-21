package com.kizuna.controller.central;

import com.kizuna.model.dto.central.tenant.PaginatedTenantVO;
import com.kizuna.model.dto.central.tenant.TenantCreateDTO;
import com.kizuna.model.dto.central.tenant.TenantStatusVO;
import com.kizuna.model.dto.central.tenant.TenantUpdateDTO;
import com.kizuna.model.dto.central.tenant.TenantVO;
import com.kizuna.service.central.tenant.CentralTenantService;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Log4j2
@RestController
@RequestMapping("/central")
@RequiredArgsConstructor
public class CentralTenantController {

  private final CentralTenantService tenantService;

  @GetMapping("tenants")
  @RolesAllowed("ADMIN")
  public ResponseEntity<PaginatedTenantVO<TenantVO>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(name = "per_page", defaultValue = "10") int perPage,
      @RequestParam(required = false) String search) {
    return ResponseEntity.ok(tenantService.list(page, perPage, search));
  }

  @GetMapping("tenant/{id}")
  @RolesAllowed("ADMIN")
  public ResponseEntity<TenantVO> getById(@PathVariable String id) {
    return tenantService
        .getById(id)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  /**
   * Get tenant by domain - Accessible to all (no authentication required) - It is for frontend to
   * get tenant info in middleware
   *
   * @param domain the domain of the tenant
   * @return TenantDto
   */
  @GetMapping(value = "tenant", params = "domain")
  @PermitAll
  public ResponseEntity<TenantVO> getByDomain(@RequestParam String domain) {
    return tenantService
        .getByDomain(domain)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PostMapping("tenant")
  @RolesAllowed("ADMIN")
  public ResponseEntity<Void> create(@Valid @RequestBody TenantCreateDTO tenant) {
    tenantService.create(tenant);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("tenant/{id}")
  @RolesAllowed("ADMIN")
  public ResponseEntity<Void> update(@PathVariable String id, @RequestBody TenantUpdateDTO tenant) {
    tenantService.update(id, tenant);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("tenant/{id}")
  @RolesAllowed("ADMIN")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    tenantService.delete(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("tenants/stats")
  @RolesAllowed("ADMIN")
  public ResponseEntity<TenantStatusVO> stats() {
    return ResponseEntity.ok(tenantService.stats());
  }
}
