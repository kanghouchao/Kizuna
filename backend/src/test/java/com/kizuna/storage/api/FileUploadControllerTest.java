package com.kizuna.storage.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.storage.api.dto.FileUploadResponse;
import com.kizuna.storage.application.FileStorageService;
import com.kizuna.storage.application.StoredFileAudit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class FileUploadControllerTest {

  @Mock private FileStorageService fileStorageService;
  @Mock private MultipartFile file;
  @Mock private StoredFileAudit storedFileAudit;

  private FileUploadController controller;

  @BeforeEach
  void setUp() {
    AppProperties appProperties = new AppProperties();
    appProperties.setUpload(new AppProperties.Upload());
    controller = new FileUploadController(fileStorageService, appProperties, storedFileAudit);
  }

  @Test
  @DisplayName("名乗った店舗と用途で保存し、保存先の公開 URL を返すこと")
  void upload_storesUnderNamedStoreAndPurpose() {
    when(fileStorageService.store(5L, "public", "store-logo", file))
        .thenReturn("public/5/store-logo/y.jpg");
    when(file.getOriginalFilename()).thenReturn("y.jpg");
    when(file.getSize()).thenReturn(4L);

    ResponseEntity<FileUploadResponse> res = controller.upload(5L, file, "store-logo", "public");

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    verify(fileStorageService).store(5L, "public", "store-logo", file);
    assertThat(res.getBody().getUrl()).isEqualTo("/static/uploads/public/5/store-logo/y.jpg");
  }

  @Test
  @DisplayName("保存が成功した後に、保存先の相対パス・bucket・サイズ・種別で監査を依頼すること")
  void upload_recordsAuditAfterStore() {
    when(fileStorageService.store(5L, "public", "store-logo", file))
        .thenReturn("public/5/store-logo/y.jpg");
    when(file.getOriginalFilename()).thenReturn("y.jpg");
    when(file.getSize()).thenReturn(4L);
    when(file.getContentType()).thenReturn("image/jpeg");

    controller.upload(5L, file, "store-logo", "public");

    var order = inOrder(fileStorageService, storedFileAudit);
    order.verify(fileStorageService).store(5L, "public", "store-logo", file);
    order
        .verify(storedFileAudit)
        .recordUpload(5L, "public/5/store-logo/y.jpg", "public", 4L, "image/jpeg");
  }

  @Test
  @DisplayName("保存が失敗したら監査を依頼しないこと")
  void upload_doesNotRecordAuditWhenStoreFails() {
    when(fileStorageService.store(5L, "public", "store-logo", file))
        .thenThrow(new IllegalStateException("s3"));

    assertThatThrownBy(() -> controller.upload(5L, file, "store-logo", "public"))
        .isInstanceOf(IllegalStateException.class);

    verifyNoInteractions(storedFileAudit);
  }

  @Test
  @DisplayName("保存が要求誤りで拒否されたら監査を依頼しないこと")
  void upload_doesNotRecordAuditWhenStoreRejectsRequest() {
    when(fileStorageService.store(5L, "public", "store-logo", file))
        .thenThrow(new ServiceException("長すぎる"));

    assertThatThrownBy(() -> controller.upload(5L, file, "store-logo", "public"))
        .isInstanceOf(ServiceException.class);

    verifyNoInteractions(storedFileAudit);
  }
}
