package com.kizuna.recruitment.infrastructure;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.ResourceBusyException;
import com.kizuna.shared.exception.ServiceUnavailableException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

public class PrivateAttachmentStorage implements AutoCloseable {
  private final S3Client client;
  private final String bucket;
  private static final long MAX_STORED_FILE_BYTES = 10L * 1024 * 1024;
  private final Path temporaryDirectory;
  private final Semaphore downloadPermits;
  private final int storageTimeoutSeconds;
  private final ScheduledExecutorService deadlines =
      Executors.newSingleThreadScheduledExecutor(
          Thread.ofPlatform().daemon().name("attachment-storage-deadline").factory());

  public PrivateAttachmentStorage(AppProperties properties) {
    this(properties, () -> createClient(properties.getPrivateAttachments()));
  }

  // 有効なのに検査を満たさない構成は、黙って 503 に落とさず起動を失敗させる。
  // 非公開バケットは基盤が宣言するので、ここでは存在と policy の不在を読むだけで、作成も policy の書き込みもしない。
  PrivateAttachmentStorage(AppProperties properties, Supplier<S3Client> factory) {
    boolean configured = properties.getPrivateAttachments().isEnabled();
    if (configured) {
      List<String> violations = isolationViolations(properties);
      if (!violations.isEmpty()) {
        deadlines.shutdownNow();
        throw startupRefused(String.join("、", violations));
      }
    }
    storageTimeoutSeconds = properties.getPrivateAttachments().getStorageTimeoutSeconds();
    client = configured ? Objects.requireNonNull(factory.get()) : null;
    bucket = configured ? properties.getPrivateAttachments().getBucket() : null;
    if (configured) {
      try {
        verifyPrivateBucket();
      } catch (RuntimeException failure) {
        close();
        throw failure;
      }
    }
    temporaryDirectory =
        configured ? Path.of(properties.getPrivateAttachments().getTemporaryDirectory()) : null;
    downloadPermits =
        new Semaphore(Math.max(0, properties.getPrivateAttachments().getMaxConcurrentDownloads()));
  }

  public boolean isConfigured() {
    return client != null;
  }

  public void requireConfigured() {
    if (!isConfigured()) throw new ServiceUnavailableException("非公開添付ストレージが設定されていません");
  }

  public void ensureStored(AttachmentObject object, Path source) {
    requireConfigured();
    validateSize(object);
    try {
      if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)
          || Files.size(source) != object.sizeBytes()) throw mismatch();
      try (InputStream input = Files.newInputStream(source)) {
        verifyContent(input, OutputStream.nullOutputStream(), object);
      }
      client.putObject(
          PutObjectRequest.builder()
              .bucket(bucket)
              .key(key(object))
              .contentType(object.mediaType())
              .contentLength(object.sizeBytes())
              .ifNoneMatch("*")
              .checksumSHA256(
                  Base64.getEncoder().encodeToString(HexFormat.of().parseHex(object.sha256())))
              .build(),
          RequestBody.fromFile(source));
    } catch (S3Exception exception) {
      if (exception.statusCode() != 412) throw unavailable();
      try (PrivateAttachmentFile _ = readVerified(object)) {
        // 条件付き作成の競合は、既存オブジェクトの実バイトが同一の場合だけ成功とする。
      } catch (IOException cleanupFailure) {
        throw unavailable();
      }
    } catch (IOException | SdkException exception) {
      throw unavailable();
    }
  }

  /** 実体が既に無い場合も成功とする。削除後に DB 更新が失敗しても、再実行が同じ添付を削除し直せるようにするため。 */
  public void delete(AttachmentObject object) {
    requireConfigured();
    try {
      client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key(object)).build());
    } catch (S3Exception exception) {
      if (exception.statusCode() != 404) throw unavailable();
    } catch (SdkException exception) {
      throw unavailable();
    }
  }

  public void verifyMetadata(AttachmentObject object) {
    requireConfigured();
    validateSize(object);
    try {
      var metadata =
          client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key(object)).build());
      if (!Objects.equals(metadata.contentLength(), object.sizeBytes())
          || !Objects.equals(metadata.contentType(), object.mediaType())) throw unavailable();
    } catch (SdkException exception) {
      throw unavailable();
    }
  }

  public PrivateAttachmentFile readVerified(AttachmentObject object) {
    requireConfigured();
    validateSize(object);
    if (!downloadPermits.tryAcquire()) throw new ResourceBusyException("添付取得が混み合っています。再試行してください");
    Path temporary = null;
    boolean handedOff = false;
    try {
      Files.createDirectories(
          temporaryDirectory,
          PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
      if (Files.isSymbolicLink(temporaryDirectory)
          || !Files.getPosixFilePermissions(temporaryDirectory)
              .equals(PosixFilePermissions.fromString("rwx------"))) throw unavailable();
      temporary =
          Files.createTempFile(
              temporaryDirectory,
              "attachment-",
              ".tmp",
              PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
      AtomicBoolean expired = new AtomicBoolean();
      AtomicReference<ResponseInputStream<GetObjectResponse>> stream = new AtomicReference<>();
      var deadline =
          deadlines.schedule(
              () -> {
                expired.set(true);
                var active = stream.get();
                if (active != null) active.abort();
              },
              storageTimeoutSeconds,
              TimeUnit.SECONDS);
      try (ResponseInputStream<GetObjectResponse> input =
              client.getObject(GetObjectRequest.builder().bucket(bucket).key(key(object)).build());
          OutputStream output = Files.newOutputStream(temporary)) {
        stream.set(input);
        try {
          if (expired.get()) throw unavailable();
          if (!Objects.equals(input.response().contentLength(), object.sizeBytes())
              || !Objects.equals(input.response().contentType(), object.mediaType()))
            throw mismatch();
          verifyContent(input, output, object);
          if (expired.get()) throw unavailable();
        } catch (IOException | RuntimeException invalidContent) {
          input.abort();
          if (expired.get()) throw unavailable();
          throw invalidContent;
        }
      } finally {
        deadline.cancel(false);
        stream.set(null);
      }
      PrivateAttachmentFile result = new PrivateAttachmentFile(temporary, downloadPermits::release);
      handedOff = true;
      return result;
    } catch (S3Exception exception) {
      if (exception.statusCode() == 404) throw new AttachmentStorageMissingException();
      throw unavailable();
    } catch (IOException | SdkException exception) {
      throw unavailable();
    } finally {
      if (!handedOff) {
        try {
          if (temporary != null) Files.deleteIfExists(temporary);
        } catch (IOException cleanupFailure) {
          throw unavailable();
        } finally {
          downloadPermits.release();
        }
      }
    }
  }

  private void validateSize(AttachmentObject object) {
    if (object.sizeBytes() > MAX_STORED_FILE_BYTES) throw mismatch();
  }

  private static void verifyContent(InputStream input, OutputStream output, AttachmentObject object)
      throws IOException {
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("画像の整合性検証が利用できません");
    }
    byte[] buffer = new byte[8192];
    long total = 0;
    int count;
    while ((count =
            input.read(buffer, 0, (int) Math.min(buffer.length, object.sizeBytes() - total + 1)))
        != -1) {
      total += count;
      if (total > object.sizeBytes()) throw mismatch();
      digest.update(buffer, 0, count);
      output.write(buffer, 0, count);
    }
    if (total != object.sizeBytes()
        || !MessageDigest.isEqual(digest.digest(), HexFormat.of().parseHex(object.sha256())))
      throw mismatch();
  }

  private static String key(AttachmentObject object) {
    return "applicant-images/" + object.id();
  }

  private static ConflictException mismatch() {
    return new ConflictException("添付の内容を確認できません。保存済みデータを上書きせず回復を待ちます");
  }

  private static ServiceUnavailableException unavailable() {
    return new ServiceUnavailableException("非公開添付ストレージを利用できません。再試行してください");
  }

  @Override
  public void close() {
    deadlines.shutdownNow();
    if (client != null) client.close();
  }

  private void verifyPrivateBucket() {
    try {
      client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
    } catch (S3Exception exception) {
      if (exception.statusCode() == 404) throw startupRefused("非公開バケットが存在しない");
      throw startupRefused("非公開バケットを確認できない（HTTP " + exception.statusCode() + "）");
    } catch (SdkException exception) {
      throw startupRefused("非公開バケットを確認できない（接続失敗）");
    }
    // SeaweedFS は policy 未設定もバケット不在も 404 で返すため、エラーコードで判定する。
    try {
      client.getBucketPolicy(GetBucketPolicyRequest.builder().bucket(bucket).build());
    } catch (S3Exception exception) {
      if (exception.awsErrorDetails() != null
          && "NoSuchBucketPolicy".equals(exception.awsErrorDetails().errorCode())) return;
      throw startupRefused("非公開バケットの policy を確認できない（HTTP " + exception.statusCode() + "）");
    } catch (SdkException exception) {
      throw startupRefused("非公開バケットの policy を確認できない（接続失敗）");
    }
    throw startupRefused("非公開バケットに policy が設定されている");
  }

  private static IllegalStateException startupRefused(String reason) {
    return new IllegalStateException("非公開添付ストレージの起動時検査を満たしません: " + reason);
  }

  private static List<String> isolationViolations(AppProperties properties) {
    var privateStorage = properties.getPrivateAttachments();
    var publicStorage = properties.getUpload();
    List<String> violations = new ArrayList<>();
    URI privateEndpoint = endpointOrNull(privateStorage.getEndpoint());
    URI publicEndpoint = endpointOrNull(publicStorage.getEndpoint());
    if (privateEndpoint == null) violations.add("endpoint が未設定または不正");
    if (publicEndpoint == null) violations.add("公開側 endpoint が未設定または不正");
    if (privateEndpoint != null && privateEndpoint.equals(publicEndpoint))
      violations.add("endpoint が公開側と同じ");
    String privateBucket = privateStorage.getBucket();
    if (blank(privateBucket)) violations.add("bucket が未設定");
    else {
      if (privateBucket.equalsIgnoreCase(publicStorage.getBucket()))
        violations.add("bucket が公開側と同じ");
      if (!privateBucket.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]")) violations.add("bucket 名が不正");
    }
    if (blank(privateStorage.getAccessKey())) violations.add("accessKey が未設定");
    else if (privateStorage.getAccessKey().equals(publicStorage.getAccessKey()))
      violations.add("accessKey が公開側と同じ");
    if (blank(privateStorage.getSecretKey())) violations.add("secretKey が未設定");
    else if (privateStorage.getSecretKey().equals(publicStorage.getSecretKey()))
      violations.add("secretKey が公開側と同じ");
    if (!within(privateStorage.getMaxFileBytes(), 10L * 1024 * 1024))
      violations.add("maxFileBytes が範囲外");
    if (!within(privateStorage.getMaxApplicantFiles(), 20))
      violations.add("maxApplicantFiles が範囲外");
    if (!within(privateStorage.getMaxApplicantBytes(), 200L * 1024 * 1024))
      violations.add("maxApplicantBytes が範囲外");
    if (!within(privateStorage.getMaxConcurrentDownloads(), 2))
      violations.add("maxConcurrentDownloads が範囲外");
    if (blank(privateStorage.getTemporaryDirectory())) violations.add("temporaryDirectory が未設定");
    if (!within(privateStorage.getStorageTimeoutSeconds(), 15))
      violations.add("storageTimeoutSeconds が範囲外");
    if (!within(privateStorage.getStorageAttemptTimeoutSeconds(), 5))
      violations.add("storageAttemptTimeoutSeconds が範囲外");
    if (privateStorage.getStorageAttemptTimeoutSeconds()
        > privateStorage.getStorageTimeoutSeconds())
      violations.add("storageAttemptTimeoutSeconds が storageTimeoutSeconds を超える");
    return violations;
  }

  private static boolean within(long value, long max) {
    return value > 0 && value <= max;
  }

  private static URI endpointOrNull(String endpoint) {
    try {
      return normalizedEndpoint(endpoint);
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }

  private static URI normalizedEndpoint(String endpoint) {
    if (blank(endpoint)) throw new IllegalArgumentException("保存先が未設定です");
    URI uri = URI.create(endpoint);
    String scheme = uri.getScheme();
    if (scheme == null
        || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
        || uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getQuery() != null
        || uri.getFragment() != null
        || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) {
      throw new IllegalArgumentException("保存先が不正です");
    }
    int port = uri.getPort() == -1 ? (scheme.equalsIgnoreCase("https") ? 443 : 80) : uri.getPort();
    return URI.create(
        scheme.toLowerCase(Locale.ROOT)
            + "://"
            + uri.getHost().toLowerCase(Locale.ROOT)
            + ":"
            + port);
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static S3Client createClient(AppProperties.PrivateAttachments settings) {
    return S3Client.builder()
        .endpointOverride(normalizedEndpoint(settings.getEndpoint()))
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(settings.getAccessKey(), settings.getSecretKey())))
        .region(Region.US_EAST_1)
        .forcePathStyle(true)
        .httpClientBuilder(
            Apache5HttpClient.builder()
                .maxConnections(
                    settings.getMaxConcurrentUploads() + settings.getMaxConcurrentDownloads())
                .connectionTimeout(Duration.ofSeconds(settings.getStorageAttemptTimeoutSeconds()))
                .socketTimeout(Duration.ofSeconds(settings.getStorageAttemptTimeoutSeconds())))
        .overrideConfiguration(
            config ->
                config
                    .apiCallTimeout(Duration.ofSeconds(settings.getStorageTimeoutSeconds()))
                    .apiCallAttemptTimeout(
                        Duration.ofSeconds(settings.getStorageAttemptTimeoutSeconds())))
        .build();
  }
}
