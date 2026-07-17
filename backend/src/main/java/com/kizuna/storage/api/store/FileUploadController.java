package com.kizuna.storage.api.store;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.tenancy.TenantContext;
import com.kizuna.shared.tenancy.TenantOptional;
import com.kizuna.storage.api.dto.FileUploadResponse;
import com.kizuna.storage.application.FileStorageService;
import com.kizuna.user.domain.Capability;
import lombok.RequiredArgsConstructor;
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
  private final TenantContext tenantContext;
  private final AppProperties appProperties;

  @PostMapping("/upload")
  @PreAuthorize("isAuthenticated()")
  @TenantOptional
  public ResponseEntity<FileUploadResponse> upload(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "bucket", defaultValue = "public") String bucket) {
    String prefix = resolveStoragePrefix();
    String relativePath = fileStorageService.store(prefix, bucket, file);

    FileUploadResponse response =
        FileUploadResponse.builder()
            .url(appProperties.getUpload().getUrlPrefix() + relativePath)
            .originalName(file.getOriginalFilename())
            .size(file.getSize())
            .build();

    return ResponseEntity.ok(response);
  }

  /**
   * 保存先プレフィクスを決める。テナント文脈があればそのテナント配下、無ければ中央領域（central）へ保存する。
   *
   * <p>中央領域への保存は中央資産管理能力（{@code PERM_CENTRAL_ASSET_MANAGE}）の保持者のみ許可する。テナント文脈を解決できない 店舗系・キャスト等は
   * {@code @TenantOptional} の許可経路に乗るため、資産を中央共有領域へ誤って保存しないよう fail-closed で 403 拒否する（#287 / #322 /
   * #326 / #398）。
   */
  private String resolveStoragePrefix() {
    if (tenantContext.isTenant()) {
      return String.valueOf(tenantContext.getTenantId());
    }
    if (!hasCentralAssetManage()) {
      throw new AccessDeniedException("中央領域へのアップロードは中央資産管理能力の保持者のみ許可されています");
    }
    return "central";
  }

  private boolean hasCentralAssetManage() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return false;
    }
    String required = Capability.CENTRAL_ASSET_MANAGE.authority();
    return authentication.getAuthorities().stream()
        .anyMatch(granted -> required.equals(granted.getAuthority()));
  }
}
