package com.kizuna.controller.tenant;

import com.kizuna.config.interceptor.TenantContext;
import com.kizuna.model.dto.tenant.file.FileUploadResponse;
import com.kizuna.service.storage.FileStorageService;
import jakarta.annotation.security.PermitAll;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

  @PermitAll
  @GetMapping("/{tenantId}/{directory}/{filename}")
  public ResponseEntity<Resource> serve(
      @PathVariable Long tenantId, @PathVariable String directory, @PathVariable String filename) {
    String filePath = tenantId + "/" + directory + "/" + filename;
    Resource resource = fileStorageService.load(filePath);

    String contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
    if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
      contentType = MediaType.IMAGE_JPEG_VALUE;
    } else if (filename.endsWith(".png")) {
      contentType = MediaType.IMAGE_PNG_VALUE;
    } else if (filename.endsWith(".gif")) {
      contentType = MediaType.IMAGE_GIF_VALUE;
    } else if (filename.endsWith(".webp")) {
      contentType = "image/webp";
    }

    return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, contentType).body(resource);
  }
}
