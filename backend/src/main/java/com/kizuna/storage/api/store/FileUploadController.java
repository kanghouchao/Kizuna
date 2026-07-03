package com.kizuna.storage.api.store;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.tenancy.TenantContext;
import com.kizuna.storage.api.dto.FileUploadResponse;
import com.kizuna.storage.application.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
  public ResponseEntity<FileUploadResponse> upload(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "bucket", defaultValue = "public") String bucket) {
    String prefix =
        tenantContext.isTenant() ? String.valueOf(tenantContext.getTenantId()) : "central";
    String relativePath = fileStorageService.store(prefix, bucket, file);

    FileUploadResponse response =
        FileUploadResponse.builder()
            .url(appProperties.getUpload().getUrlPrefix() + relativePath)
            .originalName(file.getOriginalFilename())
            .size(file.getSize())
            .build();

    return ResponseEntity.ok(response);
  }
}
