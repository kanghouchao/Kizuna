package com.kizuna.recruitment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.ServiceUnavailableException;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.api.AbstractThrowableAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.GetBucketPolicyResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

class PrivateAttachmentStorageTest {
  private static final String NO_SUCH_BUCKET_POLICY =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Error><Code>NoSuchBucketPolicy</Code>"
          + "<Message>The bucket policy does not exist</Message></Error>";
  @TempDir Path temporary;

  @Test
  void lowerNewUploadLimitDoesNotInvalidateStoredDownloadsOrHead() throws Exception {
    var properties = configured();
    properties.getPrivateAttachments().setMaxFileBytes(2);
    properties.getPrivateAttachments().setTemporaryDirectory(temporary.toString());
    var client = verifiedClient();
    byte[] bytes = {1, 2, 3};
    var object = descriptor(bytes);
    when(client.getObject(any(GetObjectRequest.class))).thenReturn(response(bytes));
    when(client.headObject(any(HeadObjectRequest.class)))
        .thenReturn(
            HeadObjectResponse.builder().contentLength(3L).contentType("image/png").build());
    try (var storage = new PrivateAttachmentStorage(properties, () -> client)) {
      storage.verifyMetadata(object);
      try (var downloaded = storage.readVerified(object)) {
        assertThat(Files.readAllBytes(downloaded.path())).containsExactly(bytes);
      }
    }
  }

  @Test
  void storedDescriptorAboveAbsoluteSafetyLimitNeverReachesStorage() {
    var client = verifiedClient();
    var object =
        new AttachmentObject(UUID.randomUUID(), "image/png", 10L * 1024 * 1024 + 1, "a".repeat(64));
    try (var storage = new PrivateAttachmentStorage(configured(), () -> client)) {
      assertThatThrownBy(() -> storage.verifyMetadata(object))
          .isInstanceOf(ConflictException.class);
      assertThatThrownBy(() -> storage.readVerified(object)).isInstanceOf(ConflictException.class);
      verify(client, never()).headObject(any(HeadObjectRequest.class));
      verify(client, never()).getObject(any(GetObjectRequest.class));
    }
  }

  @Test
  void realHttpTrickleCannotOutliveWholeDownloadDeadline() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var executor = Executors.newVirtualThreadPerTaskExecutor();
    server.setExecutor(executor);
    server.createContext(
        "/",
        exchange -> {
          if (exchange.getRequestMethod().equals("HEAD")) {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
          }
          if ("policy".equals(exchange.getRequestURI().getQuery())) {
            byte[] body = NO_SUCH_BUCKET_POLICY.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/xml");
            exchange.sendResponseHeaders(404, body.length);
            try (var output = exchange.getResponseBody()) {
              output.write(body);
            }
            return;
          }
          exchange.getResponseHeaders().set("Content-Type", "image/png");
          exchange.sendResponseHeaders(200, 100);
          try (var output = exchange.getResponseBody()) {
            for (int index = 0; index < 100; index++) {
              output.write(0);
              output.flush();
              try {
                Thread.sleep(100);
              } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
              }
            }
          } catch (IOException ignored) {
          }
        });
    server.start();
    var properties = configured();
    properties
        .getPrivateAttachments()
        .setEndpoint("http://127.0.0.1:" + server.getAddress().getPort());
    properties.getPrivateAttachments().setTemporaryDirectory(temporary.toString());
    properties.getPrivateAttachments().setStorageTimeoutSeconds(1);
    properties.getPrivateAttachments().setStorageAttemptTimeoutSeconds(1);
    long start = System.nanoTime();
    try (var storage = new PrivateAttachmentStorage(properties)) {
      assertThatThrownBy(() -> storage.readVerified(descriptor(new byte[100])))
          .isInstanceOf(ServiceUnavailableException.class);
      assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(4));
    } finally {
      server.stop(0);
      executor.shutdownNow();
    }
  }

  @Test
  void tricklingDownloadIsAbortedAtWholeTransferDeadlineAndReleasesSlot() throws Exception {
    var properties = configured();
    properties.getPrivateAttachments().setTemporaryDirectory(temporary.toString());
    properties.getPrivateAttachments().setStorageTimeoutSeconds(1);
    properties.getPrivateAttachments().setStorageAttemptTimeoutSeconds(1);
    properties.getPrivateAttachments().setMaxConcurrentDownloads(1);
    var client = verifiedClient();
    byte[] bytes = new byte[10000];
    var object = descriptor(bytes);
    @SuppressWarnings("unchecked")
    ResponseInputStream<GetObjectResponse> input = mock(ResponseInputStream.class);
    when(input.response())
        .thenReturn(
            GetObjectResponse.builder()
                .contentLength((long) bytes.length)
                .contentType("image/png")
                .build());
    var aborted = new AtomicBoolean();
    doAnswer(
            call -> {
              aborted.set(true);
              return null;
            })
        .when(input)
        .abort();
    when(input.read(any(byte[].class), anyInt(), anyInt()))
        .thenAnswer(
            call -> {
              if (aborted.get()) throw new IOException("検証用中断");
              Thread.sleep(10);
              ((byte[]) call.getArgument(0))[0] = 0;
              return 1;
            });
    var firstResponse = new AtomicBoolean(true);
    when(client.getObject(any(GetObjectRequest.class)))
        .thenAnswer(call -> firstResponse.getAndSet(false) ? input : response(bytes));
    long start = System.nanoTime();
    try (var storage = new PrivateAttachmentStorage(properties, () -> client)) {
      assertThatThrownBy(() -> storage.readVerified(object))
          .isInstanceOf(ServiceUnavailableException.class);
      assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
      assertThat(aborted).isTrue();
      try (var downloaded = storage.readVerified(object)) {
        assertThat(Files.size(downloaded.path())).isEqualTo(bytes.length);
      }
    }
    try (var paths = Files.list(temporary)) {
      assertThat(paths).isEmpty();
    }
  }

  @Test
  void existingConditionalObjectIsVerifiedAndNeverOverwrittenOrDeleted() throws Exception {
    AppProperties properties = configured();
    properties.getPrivateAttachments().setTemporaryDirectory(temporary.toString());
    S3Client client = verifiedClient();
    byte[] bytes = new byte[] {1, 2, 3};
    AttachmentObject object = descriptor(bytes);
    Path source = Files.write(temporary.resolve("source"), bytes);
    when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenThrow(S3Exception.builder().statusCode(412).build());
    when(client.getObject(any(GetObjectRequest.class))).thenReturn(response(bytes));
    try (PrivateAttachmentStorage storage =
        new PrivateAttachmentStorage(properties, () -> client)) {
      storage.ensureStored(object, source);
    }
    ArgumentCaptor<PutObjectRequest> put = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(client).putObject(put.capture(), any(RequestBody.class));
    assertThat(put.getValue().ifNoneMatch()).isEqualTo("*");
    assertThat(put.getValue().bucket()).isEqualTo("applicant-images");
    assertThat(put.getValue().key()).isEqualTo("applicant-images/" + object.id());
    verify(client, never()).deleteObject(any(DeleteObjectRequest.class));
    try (var files = Files.list(temporary)) {
      assertThat(files.toList()).containsExactly(source);
    }
  }

  @Test
  void sameSizeCorruptionIsRefusedBeforeDeliveryAndTemporaryFileIsRemoved() throws Exception {
    AppProperties properties = configured();
    properties.getPrivateAttachments().setTemporaryDirectory(temporary.toString());
    S3Client client = verifiedClient();
    AttachmentObject object = descriptor(new byte[] {1, 2, 3});
    when(client.getObject(any(GetObjectRequest.class))).thenReturn(response(new byte[] {3, 2, 1}));
    try (PrivateAttachmentStorage storage =
        new PrivateAttachmentStorage(properties, () -> client)) {
      assertThatThrownBy(() -> storage.readVerified(object)).isInstanceOf(ConflictException.class);
    }
    verify(client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    verify(client, never()).deleteObject(any(DeleteObjectRequest.class));
    try (var files = Files.list(temporary)) {
      assertThat(files.toList()).isEmpty();
    }
  }

  @Test
  void unknownPutResultNeverFallsBackToOverwriteOrDeletion() throws Exception {
    AppProperties properties = configured();
    S3Client client = verifiedClient();
    byte[] bytes = new byte[] {1, 2, 3};
    Path source = Files.write(temporary.resolve("source"), bytes);
    AttachmentObject object = descriptor(bytes);
    when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenThrow(SdkClientException.create("保存先を含む内部例外"));
    try (PrivateAttachmentStorage storage =
        new PrivateAttachmentStorage(properties, () -> client)) {
      assertThatThrownBy(() -> storage.ensureStored(object, source))
          .isInstanceOf(ServiceUnavailableException.class)
          .hasMessage("非公開添付ストレージを利用できません。再試行してください")
          .hasNoCause();
    }
    verify(client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    verify(client, never()).getObject(any(GetObjectRequest.class));
    verify(client, never()).deleteObject(any(DeleteObjectRequest.class));
  }

  @Test
  void downloadSlotsAreBoundedAndReleasedWhenFilesAreClosed() throws Exception {
    AppProperties properties = configured();
    properties.getPrivateAttachments().setTemporaryDirectory(temporary.toString());
    S3Client client = verifiedClient();
    byte[] bytes = new byte[] {1, 2, 3};
    AttachmentObject object = descriptor(bytes);
    when(client.getObject(any(GetObjectRequest.class))).thenAnswer(invocation -> response(bytes));
    try (PrivateAttachmentStorage storage =
        new PrivateAttachmentStorage(properties, () -> client)) {
      try (PrivateAttachmentFile first = storage.readVerified(object);
          PrivateAttachmentFile _ = storage.readVerified(object)) {
        assertThat(Files.readAllBytes(first.path())).containsExactly(bytes);
        assertThatThrownBy(() -> storage.readVerified(object))
            .isInstanceOf(ServiceUnavailableException.class);
      }
      try (PrivateAttachmentFile next = storage.readVerified(object)) {
        assertThat(Files.readAllBytes(next.path())).containsExactly(bytes);
      }
    }
    try (var files = Files.list(temporary)) {
      assertThat(files.toList()).isEmpty();
    }
  }

  @Test
  void actualStreamSizeIsCheckedWhenStorageLiesAboutContentLength() throws Exception {
    AppProperties properties = configured();
    properties.getPrivateAttachments().setTemporaryDirectory(temporary.toString());
    S3Client client = verifiedClient();
    AttachmentObject object = descriptor(new byte[] {1, 2, 3});
    when(client.getObject(any(GetObjectRequest.class)))
        .thenReturn(
            new ResponseInputStream<>(
                GetObjectResponse.builder().contentLength(3L).contentType("image/png").build(),
                new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5, 6})));
    try (PrivateAttachmentStorage storage =
        new PrivateAttachmentStorage(properties, () -> client)) {
      assertThatThrownBy(() -> storage.readVerified(object)).isInstanceOf(ConflictException.class);
    }
    try (var files = Files.list(temporary)) {
      assertThat(files.toList()).isEmpty();
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 21})
  void applicantFileLimitsOutsideTheRecoveryPageBoundRefuseStartup(int limit) {
    AppProperties properties = configured();
    properties.getPrivateAttachments().setMaxApplicantFiles(limit);
    assertStartupRefusedWithoutClient(properties, "maxApplicantFiles が範囲外");
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 20})
  void applicantFileLimitsWithinTheRecoveryPageBoundRemainConfigured(int limit) {
    AppProperties properties = configured();
    properties.getPrivateAttachments().setMaxApplicantFiles(limit);
    try (var storage =
        new PrivateAttachmentStorage(properties, PrivateAttachmentStorageTest::verifiedClient)) {
      assertThat(storage.isConfigured()).isTrue();
      storage.requireConfigured();
    }
  }

  @Test
  void reusedPublicCredentialsAndIncompleteEnabledConfigurationRefuseStartup() {
    AppProperties properties = configured();
    properties.getPrivateAttachments().setSecretKey(properties.getUpload().getSecretKey());
    assertStartupRefusedWithoutClient(properties, "secretKey が公開側と同じ");
    properties = configured();
    properties.getPrivateAttachments().setAccessKey(properties.getUpload().getAccessKey());
    assertStartupRefusedWithoutClient(properties, "accessKey が公開側と同じ");
    properties = configured();
    properties.getPrivateAttachments().setEndpoint(null);
    assertStartupRefusedWithoutClient(properties, "endpoint が未設定または不正");
    properties = configured();
    properties.getPrivateAttachments().setTemporaryDirectory(null);
    assertStartupRefusedWithoutClient(properties, "temporaryDirectory が未設定");
  }

  @Test
  void deleteTreatsAMissingObjectAsDoneAndOtherFailuresAsUnavailable() throws Exception {
    S3Client client = verifiedClient();
    AttachmentObject object = descriptor(new byte[] {1, 2, 3});
    when(client.deleteObject(any(DeleteObjectRequest.class)))
        .thenReturn(null)
        .thenThrow(S3Exception.builder().statusCode(404).build())
        .thenThrow(S3Exception.builder().statusCode(500).build());
    try (PrivateAttachmentStorage storage =
        new PrivateAttachmentStorage(configured(), () -> client)) {
      storage.delete(object);
      storage.delete(object);
      assertThatThrownBy(() -> storage.delete(object))
          .isInstanceOf(ServiceUnavailableException.class);
    }
    ArgumentCaptor<DeleteObjectRequest> delete = ArgumentCaptor.forClass(DeleteObjectRequest.class);
    verify(client, times(3)).deleteObject(delete.capture());
    assertThat(delete.getValue().bucket()).isEqualTo("applicant-images");
    assertThat(delete.getValue().key()).isEqualTo("applicant-images/" + object.id());
  }

  private static AttachmentObject descriptor(byte[] bytes) throws Exception {
    return new AttachmentObject(
        UUID.randomUUID(),
        "image/png",
        bytes.length,
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
  }

  private static ResponseInputStream<GetObjectResponse> response(byte[] bytes) {
    return new ResponseInputStream<>(
        GetObjectResponse.builder()
            .contentLength((long) bytes.length)
            .contentType("image/png")
            .build(),
        new ByteArrayInputStream(bytes));
  }

  @Test
  void disabledOrMissingConfigurationNeverConstructsOrCallsClient() {
    AppProperties properties = configured();
    properties.getPrivateAttachments().setEnabled(false);
    properties.getPrivateAttachments().setAccessKey(properties.getUpload().getAccessKey());
    AtomicInteger constructions = new AtomicInteger();
    S3Client client = verifiedClient();
    try (PrivateAttachmentStorage storage =
        new PrivateAttachmentStorage(
            properties,
            () -> {
              constructions.incrementAndGet();
              return client;
            })) {
      assertThat(storage.isConfigured()).isFalse();
      assertThatThrownBy(storage::requireConfigured)
          .isInstanceOf(ServiceUnavailableException.class);
    }
    assertThat(constructions).hasValue(0);
    verifyNoInteractions(client);
  }

  @Test
  void separateVariableNamesCannotReusePublicBucketOrEndpoint() {
    AppProperties properties = configured();
    properties.getPrivateAttachments().setBucket(properties.getUpload().getBucket());
    assertStartupRefusedWithoutClient(properties, "bucket が公開側と同じ");
    properties = configured();
    properties.getPrivateAttachments().setEndpoint("http://STORAGE:8333/");
    assertStartupRefusedWithoutClient(properties, "endpoint が公開側と同じ");
  }

  @Test
  void everyFailedIsolationItemIsNamedWithoutCredentialValues() {
    AppProperties properties = configured();
    var settings = properties.getPrivateAttachments();
    settings.setAccessKey(properties.getUpload().getAccessKey());
    settings.setMaxConcurrentDownloads(3);
    assertStartupRefusedWithoutClient(properties, "accessKey が公開側と同じ")
        .hasMessageContaining("maxConcurrentDownloads が範囲外")
        .hasMessageNotContaining(settings.getAccessKey())
        .hasMessageNotContaining(settings.getSecretKey());
  }

  @Test
  void missingPrivateBucketRefusesStartupAndNeverCreatesIt() {
    S3Client client = verifiedClient();
    when(client.headBucket(any(HeadBucketRequest.class)))
        .thenThrow(S3Exception.builder().statusCode(404).build());
    assertThatThrownBy(() -> new PrivateAttachmentStorage(configured(), () -> client))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("非公開バケットが存在しない");
    verify(client, never()).createBucket(any(CreateBucketRequest.class));
    verify(client).close();
  }

  @Test
  void anyBucketPolicyOnThePrivateBucketRefusesStartup() {
    S3Client client = verifiedClient();
    when(client.getBucketPolicy(any(GetBucketPolicyRequest.class)))
        .thenReturn(GetBucketPolicyResponse.builder().policy("{}").build());
    assertThatThrownBy(() -> new PrivateAttachmentStorage(configured(), () -> client))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("非公開バケットに policy が設定されている");
    verify(client).close();
  }

  @Test
  void bucketOrPolicyThatCannotBeConfirmedRefusesStartup() {
    S3Client forbidden = verifiedClient();
    when(forbidden.headBucket(any(HeadBucketRequest.class)))
        .thenThrow(S3Exception.builder().statusCode(403).build());
    assertThatThrownBy(() -> new PrivateAttachmentStorage(configured(), () -> forbidden))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("非公開バケットを確認できない");
    S3Client unreadablePolicy = verifiedClient();
    when(unreadablePolicy.getBucketPolicy(any(GetBucketPolicyRequest.class)))
        .thenThrow(
            S3Exception.builder()
                .statusCode(403)
                .awsErrorDetails(AwsErrorDetails.builder().errorCode("AccessDenied").build())
                .build());
    assertThatThrownBy(() -> new PrivateAttachmentStorage(configured(), () -> unreadablePolicy))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("非公開バケットの policy を確認できない");
  }

  @Test
  void verifiedStartupOnlyReadsTheBucketState() {
    S3Client client = verifiedClient();
    try (var storage = new PrivateAttachmentStorage(configured(), () -> client)) {
      assertThat(storage.isConfigured()).isTrue();
    }
    var head = ArgumentCaptor.forClass(HeadBucketRequest.class);
    verify(client).headBucket(head.capture());
    assertThat(head.getValue().bucket()).isEqualTo("applicant-images");
    var policy = ArgumentCaptor.forClass(GetBucketPolicyRequest.class);
    verify(client).getBucketPolicy(policy.capture());
    assertThat(policy.getValue().bucket()).isEqualTo("applicant-images");
    verify(client, never()).createBucket(any(CreateBucketRequest.class));
    verify(client, never()).putBucketPolicy(any(PutBucketPolicyRequest.class));
  }

  private static AbstractThrowableAssert<?, ? extends Throwable> assertStartupRefusedWithoutClient(
      AppProperties properties, String failedItem) {
    AtomicInteger constructions = new AtomicInteger();
    var refused =
        assertThatThrownBy(
                () ->
                    new PrivateAttachmentStorage(
                        properties,
                        () -> {
                          constructions.incrementAndGet();
                          return verifiedClient();
                        }))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(failedItem);
    assertThat(constructions).hasValue(0);
    return refused;
  }

  private static S3Client verifiedClient() {
    S3Client client = mock(S3Client.class);
    when(client.getBucketPolicy(any(GetBucketPolicyRequest.class)))
        .thenThrow(
            S3Exception.builder()
                .statusCode(404)
                .awsErrorDetails(AwsErrorDetails.builder().errorCode("NoSuchBucketPolicy").build())
                .build());
    return client;
  }

  private static AppProperties configured() {
    AppProperties properties = new AppProperties();
    properties.getUpload().setEndpoint("http://storage:8333");
    properties.getUpload().setAccessKey(UUID.randomUUID().toString());
    properties.getUpload().setSecretKey(UUID.randomUUID().toString());
    var settings = properties.getPrivateAttachments();
    settings.setEnabled(true);
    settings.setEndpoint("http://private-storage:8333");
    settings.setBucket("applicant-images");
    settings.setAccessKey(UUID.randomUUID().toString());
    settings.setSecretKey(UUID.randomUUID().toString());
    return properties;
  }
}
