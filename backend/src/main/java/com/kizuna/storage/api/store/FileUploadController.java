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

  // JwtUtil.ISSUER_TENANT / ISSUER_PLATFORM と一致させること。storage → auth.infrastructure は Modulith 上
  // 不可視のため定数を直接参照せずローカルに複製している（発行者名はトークンの署名対象で実質不変）。
  private static final String TENANT_ISSUER = "TenantAuth";
  private static final String PLATFORM_ISSUER = "PlatformAuth";

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
   * <p>ただしテナント発行（TenantAuth）のトークンでテナント文脈を解決できない場合、およびプラットフォーム発行（PlatformAuth）の トークンの場合は 403
   * で拒否する。いずれも {@code tenantId} claim を持たず {@code @TenantOptional} の許可経路に乗るため、
   * テナント／プラットフォームの資産を中央共有領域へ誤って 保存しないよう fail-closed で弾く（#287 / #322）。中央発行（CentralAuth）は
   * テナント文脈を持たないのが正当なため従来どおり central へ保存する。
   */
  private String resolveStoragePrefix() {
    if (tenantContext.isTenant()) {
      return String.valueOf(tenantContext.getTenantId());
    }
    String issuer = tokenIssuer();
    if (TENANT_ISSUER.equals(issuer)) {
      throw new AccessDeniedException("テナント文脈を解決できないため、アップロードを拒否しました");
    }
    if (PLATFORM_ISSUER.equals(issuer)) {
      throw new AccessDeniedException("プラットフォームトークンでは中央領域へのアップロードを許可していません");
    }
    return "central";
  }

  private String tokenIssuer() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getDetails() instanceof Claims claims) {
      return claims.getIssuer();
    }
    return null;
  }
}
