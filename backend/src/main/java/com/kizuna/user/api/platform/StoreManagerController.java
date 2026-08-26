package com.kizuna.user.api.platform;

import com.kizuna.user.api.dto.StoreManagerAppointRequest;
import com.kizuna.user.api.dto.StoreManagerCandidateResponse;
import com.kizuna.user.api.dto.StoreManagerResponse;
import com.kizuna.user.application.StoreManagerService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 店長設定 API（店舗管理ページの 1 節）。全操作 ROLE_MANAGE 権限限定で、任命・解任は Owner 層の操作である（ADR 0020）。
 *
 * <p>店長は独立した記録でなく「STORE_MANAGER 保持 かつ 当該店舗を担当範囲に含む」の導出なので、集合への追加＝任命、 集合からの除去＝解任として表す。
 *
 * <p>クラス側の割当を店舗までに留めるのは、任命候補が店長の集合の部分資源ではなく兄弟の読み口だからである。
 */
@RestController
@RequestMapping("/platform/stores/{storeId}")
@RequiredArgsConstructor
public class StoreManagerController {

  private final StoreManagerService storeManagerService;

  @GetMapping("/managers")
  @PreAuthorize("hasAuthority('PERM_ROLE_MANAGE')")
  public ResponseEntity<List<StoreManagerResponse>> list(@PathVariable Long storeId) {
    return ResponseEntity.ok(storeManagerService.list(storeId));
  }

  /** 任命できる既存アカウントの候補。母集団は全スタッフアカウントで無界なので分頁する。並びは全順序（表示名 + id）。 */
  @GetMapping("/manager-candidates")
  @PreAuthorize("hasAuthority('PERM_ROLE_MANAGE')")
  public ResponseEntity<Page<StoreManagerCandidateResponse>> candidates(
      @PathVariable Long storeId,
      @RequestParam(required = false) String search,
      @PageableDefault(sort = {"displayName", "id"}) Pageable pageable) {
    return ResponseEntity.ok(storeManagerService.candidates(storeId, search, pageable));
  }

  @PostMapping("/managers")
  @PreAuthorize("hasAuthority('PERM_ROLE_MANAGE')")
  public ResponseEntity<StoreManagerResponse> appoint(
      @PathVariable Long storeId, @Valid @RequestBody StoreManagerAppointRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(storeManagerService.appoint(storeId, req));
  }

  @DeleteMapping("/managers/{userId}")
  @PreAuthorize("hasAuthority('PERM_ROLE_MANAGE')")
  public ResponseEntity<Void> dismiss(@PathVariable Long storeId, @PathVariable Long userId) {
    storeManagerService.dismiss(storeId, userId);
    return ResponseEntity.noContent().build();
  }
}
