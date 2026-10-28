package com.kizuna.storage.api;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.storescope.CurrentStore;
import com.kizuna.storage.api.dto.FileUploadResponse;
import com.kizuna.storage.application.FileStorageService;
import com.kizuna.storage.application.StoredFileAudit;
import com.kizuna.user.domain.PermissionCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileUploadController {

  private final FileStorageService fileStorageService;
  private final AppProperties appProperties;
  private final StoredFileAudit storedFileAudit;

  @PostMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<FileUploadResponse> upload(
      @CurrentStore(required = false) Long storeId,
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "bucket", defaultValue = "public") String bucket) {
    String prefix = resolveStoragePrefix(storeId);
    String relativePath = fileStorageService.store(prefix, bucket, file);
    storedFileAudit.recorded(storeId, relativePath, bucket, file.getSize(), file.getContentType());

    FileUploadResponse response =
        FileUploadResponse.builder()
            .url(appProperties.getUpload().getUrlPrefix() + relativePath)
            .originalName(file.getOriginalFilename())
            .size(file.getSize())
            .build();

    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /**
   * 保存先プレフィクスを決める。店舗を名乗ればその店舗配下、名乗らなければプラットフォーム共有領域（platform）へ保存する。
   *
   * <p>共有領域への保存はプラットフォーム資産管理能力（{@code PERM_ASSET_MANAGE}）の保持者のみ許可する。店舗を名乗らない要求は
   * 店舗の検証を経ないため、資産を共有領域へ誤って保存しないよう fail-closed で 403 拒否する。
   */
  private String resolveStoragePrefix(Long storeId) {
    if (storeId != null) {
      return String.valueOf(storeId);
    }
    if (!hasAssetManage()) {
      throw new AccessDeniedException("プラットフォーム共有領域へのアップロードはプラットフォーム資産管理能力の保持者のみ許可されています");
    }
    return "platform";
  }

  private boolean hasAssetManage() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return false;
    }
    String required = PermissionCode.ASSET_MANAGE.authority();
    return authentication.getAuthorities().stream()
        .anyMatch(granted -> required.equals(granted.getAuthority()));
  }
}
