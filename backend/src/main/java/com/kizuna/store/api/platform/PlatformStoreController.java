package com.kizuna.store.api.platform;

import com.kizuna.store.api.dto.PaginatedStoreVO;
import com.kizuna.store.api.dto.PlatformStoreResponse;
import com.kizuna.store.api.dto.StoreCreateDTO;
import com.kizuna.store.api.dto.StoreStatusVO;
import com.kizuna.store.api.dto.StoreUpdateDTO;
import com.kizuna.store.api.dto.StoreVO;
import com.kizuna.store.application.PlatformStoreService;
import com.kizuna.store.application.StoreRegistryService;
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
  private final StoreRegistryService storeRegistryService;

  @GetMapping("/me")
  @PreAuthorize("hasAuthority('PERM_STORE_VIEW')")
  public ResponseEntity<List<PlatformStoreResponse>> listAuthorized() {
    return ResponseEntity.ok(platformStoreService.listAuthorizedStores());
  }

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_STORE_MANAGE')")
  public ResponseEntity<PaginatedStoreVO<StoreVO>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(name = "per_page", defaultValue = "10") int perPage,
      @RequestParam(required = false) String search) {
    return ResponseEntity.ok(storeRegistryService.list(page, perPage, search));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_STORE_MANAGE')")
  public ResponseEntity<StoreVO> getById(@PathVariable String id) {
    return storeRegistryService
        .getById(id)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  /**
   * 公開ドメイン照会。未認証で呼べ、frontend の middleware が店舗情報を解決するために用いる。
   *
   * @param domain 照会対象の店舗ドメイン
   * @return 該当店舗の {@link StoreVO}、存在しなければ 404
   */
  @GetMapping("/lookup")
  @PermitAll
  public ResponseEntity<StoreVO> getByDomain(@RequestParam String domain) {
    return storeRegistryService
        .getByDomain(domain)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PostMapping
  @PreAuthorize("hasAuthority('PERM_STORE_MANAGE')")
  public ResponseEntity<Void> create(@Valid @RequestBody StoreCreateDTO store) {
    storeRegistryService.create(store);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_STORE_MANAGE')")
  public ResponseEntity<Void> update(@PathVariable String id, @RequestBody StoreUpdateDTO store) {
    storeRegistryService.update(id, store);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_STORE_MANAGE')")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    storeRegistryService.delete(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/stats")
  @PreAuthorize("hasAuthority('PERM_STORE_MANAGE')")
  public ResponseEntity<StoreStatusVO> stats() {
    return ResponseEntity.ok(storeRegistryService.stats());
  }
}
