package com.kizuna.store.api.store;

import com.kizuna.shared.storescope.CurrentStore;
import com.kizuna.store.application.StoreActivationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 店舗設定の子資源。現在店舗（X-Store-ID）を準備中から稼働中へ移す店長の明示操作で、繰り返しは 400。 */
@RestController
@RequestMapping("/store/config/activation")
@RequiredArgsConstructor
public class StoreActivationController {

  private final StoreActivationService storeActivationService;

  @PostMapping
  @PreAuthorize("hasAuthority('PERM_STORE_PROFILE_MANAGE')")
  public ResponseEntity<Void> activate(@CurrentStore Long storeId) {
    storeActivationService.activate(storeId);
    return ResponseEntity.noContent().build();
  }
}
