package com.kizuna.store.api.platform;

import com.kizuna.store.api.dto.PlatformStoreCreationResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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

/** 平台（統一）店舗 API。授権店舗一覧とプラットフォーム運営の店舗 CRUD を提供する（統一ログイン / 二命名空間）。 */
@RestController
@RequestMapping("/platform/stores")
@RequiredArgsConstructor
public class PlatformStoreController {

  private final PlatformStoreService platformStoreService;
  private final StoreRegistryService storeRegistryService;

  // 授権店舗一覧は跨店参照能力（PERM_STORE_VIEW）だけでなく、店舗コンソール資格（storeBridge）保持者にも開く。
  // 店舗コンソールに着地するが STORE_VIEW を持たない混成束ユーザーが自店舗を解決できるようにするため。
  // EMERGENCY_ELEVATE にも開くのは、緊急昇格の発動フォームが対象店舗の選択肢をここから引くため
  // （STORE_VIEW を伴わない自作ロールでも発動の導線が成立する）。
  // 応答は呼出者本人の授権店舗（id + name）のみ（PlatformStoreService.listAuthorizedStores が StoreScope で濾過）。
  @GetMapping("/me")
  @PreAuthorize(
      "hasAuthority('PERM_STORE_VIEW') or hasAuthority('PERM_EMERGENCY_ELEVATE')"
          + " or @storeBridge.check(authentication)")
  public ResponseEntity<List<PlatformStoreResponse>> listAuthorized() {
    return ResponseEntity.ok(platformStoreService.listAuthorizedStores());
  }

  // この一覧は副キー id も降順で揃える。補完で付く id は昇順のため、既定の並びに明示している。
  @GetMapping
  @PreAuthorize("hasAuthority('PERM_STORE_MANAGE')")
  public ResponseEntity<Page<StoreVO>> list(
      @RequestParam(required = false) String search,
      @PageableDefault(
              sort = {"createdAt", "id"},
              direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(storeRegistryService.list(search, pageable));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_STORE_MANAGE')")
  public ResponseEntity<StoreVO> getById(@PathVariable String id) {
    return ResponseEntity.ok(storeRegistryService.getById(id));
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
  public ResponseEntity<PlatformStoreCreationResponse> create(
      @Valid @RequestBody StoreCreateDTO store) {
    Long id = storeRegistryService.create(store);
    return ResponseEntity.status(HttpStatus.CREATED).body(new PlatformStoreCreationResponse(id));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_STORE_MANAGE')")
  public ResponseEntity<Void> update(
      @PathVariable String id, @Valid @RequestBody StoreUpdateDTO store) {
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
