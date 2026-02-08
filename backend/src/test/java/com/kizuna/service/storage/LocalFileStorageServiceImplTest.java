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
import org.springframework.core.io.Resource;
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
  void store_正常にファイルを保存する() throws IOException {
    byte[] content = "テストファイルの内容".getBytes();
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
  void store_許可されていないMIMEタイプでエラー() {
    when(multipartFile.getSize()).thenReturn(100L);
    when(multipartFile.getContentType()).thenReturn("application/pdf");

    assertThatThrownBy(() -> storageService.store(1L, "docs", multipartFile))
        .isInstanceOf(ServiceException.class)
        .hasMessage("許可されていないファイル形式です");
  }

  @Test
  void store_ファイルサイズ超過でエラー() {
    when(multipartFile.getSize()).thenReturn(20_000_000L);

    assertThatThrownBy(() -> storageService.store(1L, "photos", multipartFile))
        .isInstanceOf(ServiceException.class)
        .hasMessage("ファイルサイズが上限を超えています");
  }

  @Test
  void delete_正常にファイルを削除する() throws IOException {
    // テスト用ファイルを作成
    Path dir = tempDir.resolve("1/photos");
    Files.createDirectories(dir);
    Path file = dir.resolve("test.jpg");
    Files.write(file, "content".getBytes());
    assertThat(Files.exists(file)).isTrue();

    storageService.delete("1/photos/test.jpg");

    assertThat(Files.exists(file)).isFalse();
  }

  @Test
  void load_正常にファイルを読み込む() throws IOException {
    // テスト用ファイルを作成
    Path dir = tempDir.resolve("1/photos");
    Files.createDirectories(dir);
    Path file = dir.resolve("test.jpg");
    Files.write(file, "content".getBytes());

    Resource resource = storageService.load("1/photos/test.jpg");

    assertThat(resource.exists()).isTrue();
    assertThat(resource.isReadable()).isTrue();
  }

  @Test
  void load_存在しないファイルでエラー() {
    assertThatThrownBy(() -> storageService.load("1/photos/nonexistent.jpg"))
        .isInstanceOf(ServiceException.class)
        .hasMessage("ファイルが見つかりません");
  }
}
