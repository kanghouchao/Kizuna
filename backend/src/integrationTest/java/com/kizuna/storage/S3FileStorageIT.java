package com.kizuna.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.storage.application.FileStorageService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 実 MinIO に対する {@link FileStorageService}（S3FileStorage）の統合テスト。
 *
 * <p>アップロードした object に対して認証なしで直接 HTTP GET を行い、200 とアップロード内容の一致を確認する。 起動時に付与される public-read
 * ポリシーが実際に効いていることを検証する唯一の層（S3Client を mock する単体テストでは検証できない）。
 */
@SpringBootTest
class S3FileStorageIT {

  @Autowired private FileStorageService fileStorageService;

  @Autowired private AppProperties appProperties;

  @Test
  @DisplayName("アップロードした object に認証なしでアクセスでき、内容が一致すること（public-read ポリシーの実効性）")
  void uploadedObjectIsPubliclyReadable() throws Exception {
    byte[] content = "integration-test image bytes".getBytes();
    MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", content);

    String key = fileStorageService.store("1", "public", file);

    AppProperties.Upload upload = appProperties.getUpload();
    URI objectUri = URI.create(upload.getEndpoint() + "/" + upload.getBucket() + "/" + key);

    HttpResponse<byte[]> response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(objectUri).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo(content);
  }
}
