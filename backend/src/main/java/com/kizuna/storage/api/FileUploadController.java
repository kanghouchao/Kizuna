package com.kizuna.storage.api;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.storescope.CurrentStore;
import com.kizuna.storage.api.dto.FileUploadResponse;
import com.kizuna.storage.application.FileStorageService;
import com.kizuna.storage.application.StoredFileAudit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
  private final AppProperties appProperties;
  private final StoredFileAudit storedFileAudit;

  @PostMapping
  @PreAuthorize("hasAnyAuthority('PERM_STORE_PROFILE_MANAGE', 'PERM_CAST_MANAGE')")
  public ResponseEntity<FileUploadResponse> upload(
      @CurrentStore Long storeId,
      @RequestParam("file") MultipartFile file,
      @RequestParam("purpose") String purpose,
      @RequestParam(value = "bucket", defaultValue = "public") String bucket) {
    String relativePath = fileStorageService.store(storeId, bucket, purpose, file);
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
}
