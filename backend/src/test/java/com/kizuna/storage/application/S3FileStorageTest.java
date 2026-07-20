package com.kizuna.storage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceException;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@ExtendWith(MockitoExtension.class)
class S3FileStorageTest {

  @Mock private S3Client s3Client;

  @Mock private MultipartFile multipartFile;

  private S3FileStorage storageService;

  @BeforeEach
  void setUp() {
    AppProperties appProperties = new AppProperties();
    AppProperties.Upload upload = new AppProperties.Upload();
    upload.setBucket("uploads");
    upload.setMaxFileSize(10485760L);
    upload.setAllowedTypes(List.of("image/jpeg", "image/png", "image/gif", "image/webp"));
    appProperties.setUpload(upload);

    storageService = new S3FileStorage(s3Client, appProperties);
  }

  @Test
  void store_uploadsToFixedBucketWithBucketPrefixKey() throws IOException {
    byte[] content = "test content".getBytes();
    when(multipartFile.getSize()).thenReturn((long) content.length);
    when(multipartFile.getContentType()).thenReturn("image/jpeg");
    when(multipartFile.getOriginalFilename()).thenReturn("test.jpg");
    when(multipartFile.getBytes()).thenReturn(content);

    String result = storageService.store("1", "public", multipartFile);

    // 返り値（object key）は従来のローカル実装と同じ bucket/prefix/UUID.ext 形式を維持する
    assertThat(result).startsWith("public/1/");
    assertThat(result).endsWith(".jpg");

    ArgumentCaptor<PutObjectRequest> requestCaptor =
        ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
    // 実 S3 bucket は固定値 uploads。リクエストの bucket 値は object key の一部として扱う
    assertThat(requestCaptor.getValue().bucket()).isEqualTo("uploads");
    assertThat(requestCaptor.getValue().key()).isEqualTo(result);
  }

  @Test
  void store_uploadsPlatformPrefix() throws IOException {
    byte[] content = "platform content".getBytes();
    when(multipartFile.getSize()).thenReturn((long) content.length);
    when(multipartFile.getContentType()).thenReturn("image/png");
    when(multipartFile.getOriginalFilename()).thenReturn("logo.png");
    when(multipartFile.getBytes()).thenReturn(content);

    String result = storageService.store("platform", "public", multipartFile);

    assertThat(result).startsWith("public/platform/");
    assertThat(result).endsWith(".png");

    ArgumentCaptor<PutObjectRequest> requestCaptor =
        ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
    assertThat(requestCaptor.getValue().bucket()).isEqualTo("uploads");
    assertThat(requestCaptor.getValue().key()).isEqualTo(result);
  }

  @Test
  void store_throwsErrorOnInvalidPrefix() {
    assertThatThrownBy(() -> storageService.store("../../../etc", "public", multipartFile))
        .isInstanceOf(ServiceException.class)
        .hasMessage("不正なプレフィックスです");
    verifyNoInteractions(s3Client);
  }

  @Test
  void store_throwsErrorOnNullPrefix() {
    assertThatThrownBy(() -> storageService.store(null, "public", multipartFile))
        .isInstanceOf(ServiceException.class)
        .hasMessage("不正なプレフィックスです");
    verifyNoInteractions(s3Client);
  }

  @Test
  void store_throwsErrorOnInvalidBucketName() {
    assertThatThrownBy(() -> storageService.store("1", "../../../etc", multipartFile))
        .isInstanceOf(ServiceException.class)
        .hasMessage("不正なバケット名です");
    verifyNoInteractions(s3Client);
  }

  @Test
  void store_throwsErrorOnDisallowedMimeType() {
    when(multipartFile.getSize()).thenReturn(100L);
    when(multipartFile.getContentType()).thenReturn("application/pdf");

    assertThatThrownBy(() -> storageService.store("1", "public", multipartFile))
        .isInstanceOf(ServiceException.class)
        .hasMessage("許可されていないファイル形式です");
    verifyNoInteractions(s3Client);
  }

  @Test
  void store_throwsErrorOnFileSizeExceeded() {
    when(multipartFile.getSize()).thenReturn(20_000_000L);

    assertThatThrownBy(() -> storageService.store("1", "public", multipartFile))
        .isInstanceOf(ServiceException.class)
        .hasMessage("ファイルサイズが上限を超えています");
    verifyNoInteractions(s3Client);
  }

  @Test
  void run_createsBucketAndAppliesPublicReadPolicy() {
    storageService.run(null);

    verify(s3Client).createBucket(any(CreateBucketRequest.class));
    ArgumentCaptor<PutBucketPolicyRequest> policyCaptor =
        ArgumentCaptor.forClass(PutBucketPolicyRequest.class);
    verify(s3Client).putBucketPolicy(policyCaptor.capture());
    assertThat(policyCaptor.getValue().bucket()).isEqualTo("uploads");
    assertThat(policyCaptor.getValue().policy()).contains("s3:GetObject", "arn:aws:s3:::uploads/*");
  }

  @Test
  void run_isIdempotentWhenBucketAlreadyExists() {
    when(s3Client.createBucket(any(CreateBucketRequest.class)))
        .thenThrow(BucketAlreadyOwnedByYouException.builder().message("already owned").build());

    storageService.run(null);

    verify(s3Client).putBucketPolicy(any(PutBucketPolicyRequest.class));
  }
}
