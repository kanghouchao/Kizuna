package com.kizuna.cast.api.store;

import com.kizuna.cast.api.dto.CastFieldDefinitionCreateRequest;
import com.kizuna.cast.api.dto.CastFieldDefinitionResponse;
import com.kizuna.cast.api.dto.CastFieldDefinitionUpdateRequest;
import com.kizuna.cast.application.CastFieldDefinitionService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/store/casts/fields")
@RequiredArgsConstructor
public class CastFieldDefinitionController {

  private final CastFieldDefinitionService service;

  // 一覧(読み取り)は値入力担当（CAST_MANAGE 保持者）がキャスト編集フォームで活きた定義を描画するために必要なので
  // 閲覧能力（CAST_FIELD_DEF_VIEW）で許可する。定義そのものの作成・更新・削除は構造変更のため
  // 管理能力（CAST_FIELD_DEF_MANAGE — 既定束では店長のみ）限定を維持する。
  @GetMapping
  @PreAuthorize("hasAuthority('PERM_CAST_FIELD_DEF_VIEW')")
  public ResponseEntity<List<CastFieldDefinitionResponse>> list() {
    return ResponseEntity.ok(service.list());
  }

  @PostMapping
  @PreAuthorize("hasAuthority('PERM_CAST_FIELD_DEF_MANAGE')")
  public ResponseEntity<CastFieldDefinitionResponse> create(
      @Valid @RequestBody CastFieldDefinitionCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_CAST_FIELD_DEF_MANAGE')")
  public ResponseEntity<CastFieldDefinitionResponse> update(
      @PathVariable String id, @Valid @RequestBody CastFieldDefinitionUpdateRequest request) {
    return ResponseEntity.ok(service.update(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_CAST_FIELD_DEF_MANAGE')")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    service.delete(id);
    return ResponseEntity.ok().build();
  }
}
