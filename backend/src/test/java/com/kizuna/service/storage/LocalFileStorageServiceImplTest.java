package com.kizuna.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.kizuna.config.AppProperties;
import com.kizuna.exception.ServiceException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class LocalFileStorageServiceImplTest {

  @TempDir Path tempDir;

  @Mock private MultipartFile multipartFile;

  private AppProperties appProperties;
  private LocalFileStorageServiceImpl storageService;

  @BeforeEach
  void setUp() {
    appProperties = new AppProperties();
    AppProperties.Upload upload = new AppProperties.Upload();
    upload.setBasePath(tempDir.toString());
    upload.setMaxFileSize(10485760L);
    upload.setAllowedTypes(List.of("image/jpeg", "image/png", "image/gif", "image/webp"));
    appProperties.setUpload(upload);

    storageService = new LocalFileStorageServiceImpl(appProperties);
  }

  @Test
  void store_savesFileSuccessfully() throws IOException {
    byte[] content = "test content".getBytes();
    when(multipartFile.getSize()).thenReturn((long) content.length);
    when(multipartFile.getContentType()).thenReturn("image/jpeg");
    when(multipartFile.getOriginalFilename()).thenReturn("test.jpg");
    when(multipartFile.getInputStream()).thenReturn(new ByteArrayInputStream(content));

    String result = storageService.store(1L, "photos", multipartFile);

    assertThat(result).startsWith("1/photos/");
    assertThat(result).endsWith(".jpg");
    Path savedFile = tempDir.resolve(result);
    assertThat(Files.exists(savedFile)).isTrue();
    assertThat(Files.readAllBytes(savedFile)).isEqualTo(content);
  }

  @Test
  void store_throwsErrorOnInvalidDirectoryName() {
    assertThatThrownBy(() -> storageService.store(1L, "../../../etc", multipartFile))
        .isInstanceOf(ServiceException.class)
        .hasMessage("不正なディレクトリ名です");
  }

  @Test
  void store_throwsErrorOnDisallowedMimeType() {
    when(multipartFile.getSize()).thenReturn(100L);
    when(multipartFile.getContentType()).thenReturn("application/pdf");

    assertThatThrownBy(() -> storageService.store(1L, "docs", multipartFile))
        .isInstanceOf(ServiceException.class)
        .hasMessage("許可されていないファイル形式です");
  }

  @Test
  void store_throwsErrorOnFileSizeExceeded() {
    when(multipartFile.getSize()).thenReturn(20_000_000L);

    assertThatThrownBy(() -> storageService.store(1L, "photos", multipartFile))
        .isInstanceOf(ServiceException.class)
        .hasMessage("ファイルサイズが上限を超えています");
  }

  @Test
  void delete_removesFileSuccessfully() throws IOException {
    // Create test file
    Path dir = tempDir.resolve("1/photos");
    Files.createDirectories(dir);
    Path file = dir.resolve("test.jpg");
    Files.write(file, "content".getBytes());
    assertThat(Files.exists(file)).isTrue();

    storageService.delete("1/photos/test.jpg");

    assertThat(Files.exists(file)).isFalse();
  }

  @Test
  void delete_throwsErrorOnInvalidPath() {
    assertThatThrownBy(() -> storageService.delete("../../../etc/passwd"))
        .isInstanceOf(ServiceException.class)
        .hasMessage("不正なファイルパスです");
  }
}
