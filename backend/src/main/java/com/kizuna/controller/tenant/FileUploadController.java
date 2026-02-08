package com.kizuna.controller.tenant;

import com.kizuna.config.interceptor.TenantContext;
import com.kizuna.model.dto.tenant.file.FileUploadResponse;
import com.kizuna.service.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/tenant/files")
@RequiredArgsConstructor
public class FileUploadController {

  private final FileStorageService fileStorageService;
  private final TenantContext tenantContext;

  @PostMapping("/upload")
  public ResponseEntity<FileUploadResponse> upload(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "directory", defaultValue = "general") String directory) {
    Long tenantId = tenantContext.getTenantId();
    String relativePath = fileStorageService.store(tenantId, directory, file);

    FileUploadResponse response =
        FileUploadResponse.builder()
            .url("/static/tenant/files/" + relativePath)
            .originalName(file.getOriginalFilename())
            .size(file.getSize())
            .build();

    return ResponseEntity.ok(response);
  }
}
