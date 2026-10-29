package com.kizuna.storage.api;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.storescope.CurrentStore;
import com.kizuna.storage.api.dto.FileUploadResponse;
import com.kizuna.storage.application.FileStorageService;
import com.kizuna.storage.application.StoredFileAudit;
import com.kizuna.user.domain.PermissionCode;
import java.util.Arrays;
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
  @PreAuthorize(
      "hasAnyAuthority('PERM_STORE_PROFILE_MANAGE', 'PERM_CAST_MANAGE', 'PERM_ASSET_MANAGE')")
  public ResponseEntity<FileUploadResponse> upload(
      @CurrentStore(required = false) Long storeId,
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "bucket", defaultValue = "public") String bucket) {
    String prefix = resolveStoragePrefix(storeId);
    String relativePath = fileStorageService.store(prefix, bucket, file);
    storedFileAudit.recordUpload(
        storeId, relativePath, bucket, file.getSize(), file.getContentType());

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
   * <p>店舗配下への保存は店舗プロフィール管理（{@code PERM_STORE_PROFILE_MANAGE}）かキャスト管理（{@code PERM_CAST_MANAGE}）の
   * 保持者のみ、共有領域への保存は資産管理（{@code PERM_ASSET_MANAGE}）の保持者のみ許可する。店舗を名乗らない要求は店舗の検証を経ないため、
   * 共有領域へ誤って保存しないよう fail-closed で 403 拒否する。
   */
  private String resolveStoragePrefix(Long storeId) {
    if (storeId != null) {
      if (!hasAnyAuthority(PermissionCode.STORE_PROFILE_MANAGE, PermissionCode.CAST_MANAGE)) {
        throw new AccessDeniedException("店舗配下へのアップロードは店舗プロフィール管理またはキャスト管理能力の保持者のみ許可されています");
      }
      return String.valueOf(storeId);
    }
    if (!hasAnyAuthority(PermissionCode.ASSET_MANAGE)) {
      throw new AccessDeniedException("プラットフォーム共有領域へのアップロードはプラットフォーム資産管理能力の保持者のみ許可されています");
    }
    return "platform";
  }

  private boolean hasAnyAuthority(PermissionCode... codes) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return false;
    }
    return authentication.getAuthorities().stream()
        .anyMatch(
            granted ->
                Arrays.stream(codes)
                    .anyMatch(code -> code.authority().equals(granted.getAuthority())));
  }
}
