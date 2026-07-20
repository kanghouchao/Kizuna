package com.kizuna.storage.api.store;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreOptional;
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
  private final StoreContext storeContext;
  private final AppProperties appProperties;

  @PostMapping("/upload")
  @PreAuthorize("isAuthenticated()")
  @StoreOptional
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
   * 保存先プレフィクスを決める。店舗文脈があればその店舗配下、無ければプラットフォーム共有領域（platform）へ保存する。
   *
   * <p>プラットフォーム共有領域への保存はプラットフォーム資産管理能力（{@code PERM_PLATFORM_ASSET_MANAGE}）の保持者のみ許可する。店舗文脈を解決できない
   * 店舗系・キャスト等は {@code @StoreOptional} の許可経路に乗るため、資産をプラットフォーム共有領域へ誤って保存しないよう fail-closed で 403
   * 拒否する（#287 / #322 / #326 / #398）。
   */
  private String resolveStoragePrefix() {
    if (storeContext.hasStoreId()) {
      return String.valueOf(storeContext.getStoreId());
    }
    if (!hasPlatformAssetManage()) {
      throw new AccessDeniedException("プラットフォーム共有領域へのアップロードはプラットフォーム資産管理能力の保持者のみ許可されています");
    }
    return "platform";
  }

  private boolean hasPlatformAssetManage() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return false;
    }
    String required = Capability.PLATFORM_ASSET_MANAGE.authority();
    return authentication.getAuthorities().stream()
        .anyMatch(granted -> required.equals(granted.getAuthority()));
  }
}
