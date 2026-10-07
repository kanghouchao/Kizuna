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
import java.util.Base64;
import java.util.HexFormat;
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
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

public class PrivateAttachmentStorage implements AutoCloseable {
  private final S3Client client;
  private final String bucket;
  private final long maxFileBytes;
  private final Path temporaryDirectory;
  private final Semaphore downloadPermits;
  private final int storageTimeoutSeconds;
  private final ScheduledExecutorService deadlines =
      Executors.newSingleThreadScheduledExecutor(
          Thread.ofPlatform().daemon().name("attachment-storage-deadline").factory());

  public PrivateAttachmentStorage(AppProperties properties) {
    this(properties, () -> createClient(properties.getPrivateAttachments()));
  }

  PrivateAttachmentStorage(AppProperties properties, Supplier<S3Client> factory) {
    boolean configured = isolated(properties);
    storageTimeoutSeconds = properties.getPrivateAttachments().getStorageTimeoutSeconds();
    client = configured ? Objects.requireNonNull(factory.get()) : null;
    bucket = configured ? properties.getPrivateAttachments().getBucket() : null;
    maxFileBytes = properties.getPrivateAttachments().getMaxFileBytes();
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
      try (PrivateAttachmentFile ignored = readVerified(object)) {
        // 条件付き作成の競合は、既存オブジェクトの実バイトが同一の場合だけ成功とする。
      } catch (IOException cleanupFailure) {
        throw unavailable();
      }
    } catch (IOException | SdkException exception) {
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
    if (object.sizeBytes() > maxFileBytes) throw mismatch();
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

  private static boolean isolated(AppProperties properties) {
    var privateStorage = properties.getPrivateAttachments();
    var publicStorage = properties.getUpload();
    if (!privateStorage.isEnabled()
        || blank(privateStorage.getBucket())
        || blank(privateStorage.getAccessKey())
        || blank(privateStorage.getSecretKey())) return false;
    try {
      URI privateEndpoint = normalizedEndpoint(privateStorage.getEndpoint());
      URI publicEndpoint = normalizedEndpoint(publicStorage.getEndpoint());
      return !privateEndpoint.equals(publicEndpoint)
          && !privateStorage.getBucket().equalsIgnoreCase(publicStorage.getBucket())
          && !privateStorage.getAccessKey().equals(publicStorage.getAccessKey())
          && !privateStorage.getSecretKey().equals(publicStorage.getSecretKey())
          && privateStorage.getBucket().matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]")
          && privateStorage.getMaxFileBytes() > 0
          && privateStorage.getMaxFileBytes() <= 10L * 1024 * 1024
          && privateStorage.getMaxApplicantFiles() > 0
          && privateStorage.getMaxApplicantFiles() <= 20
          && privateStorage.getMaxApplicantBytes() > 0
          && privateStorage.getMaxApplicantBytes() <= 200L * 1024 * 1024
          && privateStorage.getMaxConcurrentDownloads() > 0
          && privateStorage.getMaxConcurrentDownloads() <= 2
          && !blank(privateStorage.getTemporaryDirectory())
          && privateStorage.getStorageTimeoutSeconds() > 0
          && privateStorage.getStorageTimeoutSeconds() <= 15
          && privateStorage.getStorageAttemptTimeoutSeconds() > 0
          && privateStorage.getStorageAttemptTimeoutSeconds() <= 5
          && privateStorage.getStorageAttemptTimeoutSeconds()
              <= privateStorage.getStorageTimeoutSeconds();
    } catch (IllegalArgumentException exception) {
      return false;
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
            ApacheHttpClient.builder()
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
