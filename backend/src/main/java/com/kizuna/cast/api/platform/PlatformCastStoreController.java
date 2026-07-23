package com.kizuna.cast.api.platform;

import com.kizuna.cast.api.dto.CastStoreResponse;
import com.kizuna.cast.application.CastSelfService;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 本人（キャスト）ポータルの所属店舗セレクタ API。出勤希望提出フォームの店舗選択に使う。 */
@RestController
@RequestMapping("/platform/me/stores")
@RequiredArgsConstructor
public class PlatformCastStoreController {

  private final CastSelfService castSelfService;

  @GetMapping
  @PreAuthorize("hasRole('CAST')")
  public ResponseEntity<List<CastStoreResponse>> myStores(Principal principal) {
    return ResponseEntity.ok(castSelfService.myStores(principal.getName()));
  }
}
