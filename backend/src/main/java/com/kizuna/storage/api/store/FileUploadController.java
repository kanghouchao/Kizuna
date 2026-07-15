package com.kizuna.storage.api.store;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.tenancy.TenantContext;
import com.kizuna.shared.tenancy.TenantOptional;
import com.kizuna.storage.api.dto.FileUploadResponse;
import com.kizuna.storage.application.FileStorageService;
import io.jsonwebtoken.Claims;
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
   * <p>中央領域への保存は HQ 管理者（role claim = HQ_ADMIN）のみ許可する。テナント文脈を解決できないテナントロール、および HQ_ADMIN
   * 以外の平台ロール（店長・スタッフ・キャスト等）は {@code tenantId} claim を持たず {@code @TenantOptional} の許可経路に乗るため、
   * 資産を中央共有領域へ誤って保存しないよう fail-closed で 403 拒否する（#287 / #322 / #326）。
   */
  private String resolveStoragePrefix() {
    if (tenantContext.isTenant()) {
      return String.valueOf(tenantContext.getTenantId());
    }
    if (!"HQ_ADMIN".equals(tokenRole())) {
      throw new AccessDeniedException("中央領域へのアップロードは HQ 管理者のみ許可されています");
    }
    return "central";
  }

  private String tokenRole() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getDetails() instanceof Claims claims) {
      return claims.get("role", String.class);
    }
    return null;
  }
}
