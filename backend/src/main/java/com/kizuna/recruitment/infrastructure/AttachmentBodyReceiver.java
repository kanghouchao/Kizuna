package com.kizuna.recruitment.infrastructure;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ResourceBusyException;
import com.kizuna.shared.exception.ServiceUnavailableException;
import com.kizuna.shared.exception.UploadInputException;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class AttachmentBodyReceiver implements AutoCloseable {
  private final AppProperties.PrivateAttachments settings;
  private final Semaphore permits;
  private final ScheduledExecutorService deadlines =
      Executors.newSingleThreadScheduledExecutor(
          Thread.ofPlatform().daemon().name("attachment-receive-deadline").factory());

  public AttachmentBodyReceiver(AppProperties properties) {
    settings = properties.getPrivateAttachments();
    if (settings.getMaxConcurrentUploads() < 1
        || settings.getMaxConcurrentUploads() > 2
        || settings.getReceiveTimeoutSeconds() < 1
        || settings.getReceiveTimeoutSeconds() > 30) {
      throw new IllegalArgumentException("画像受信の制限設定が不正です");
    }
    permits = new Semaphore(settings.getMaxConcurrentUploads());
  }

  public Admission admit(HttpServletRequest request) {
    String type = request.getContentType();
    type = type == null ? "" : type.toLowerCase(Locale.ROOT);
    String encoding = request.getHeader("Content-Encoding");
    if (!Set.of("image/jpeg", "image/png").contains(type)
        || encoding != null && !encoding.equalsIgnoreCase("identity")) {
      throw new UploadInputException(
          UploadInputException.Reason.TYPE, "JPEGまたはPNG画像を圧縮転送せず送信してください");
    }
    if (request.getContentLengthLong() > settings.getMaxFileBytes()) throw tooLarge();
    if (!permits.tryAcquire()) throw new ResourceBusyException("画像受信が混み合っています。再試行してください");
    return new Admission(type);
  }

  @Override
  public void close() {
    deadlines.shutdownNow();
  }

  public final class Admission implements AutoCloseable {
    private final String mediaType;
    private final CompletableFuture<Received> result = new CompletableFuture<>();
    private ScheduledFuture<?> timeout;
    private Path path;
    private OutputStream output;
    private long bytes;
    private boolean closed;
    private boolean started;

    private Admission(String mediaType) {
      this.mediaType = mediaType;
      timeout =
          deadlines.schedule(
              () -> fail(new ServiceUnavailableException("画像の受信が制限時間を超えました")),
              settings.getReceiveTimeoutSeconds(),
              TimeUnit.SECONDS);
    }

    public Received receive(HttpServletRequest request) {
      try {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        ServletInputStream input;
        synchronized (this) {
          if (closed || started) throw busy();
          started = true;
          Path directory = Path.of(settings.getTemporaryDirectory());
          Files.createDirectories(
              directory,
              PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
          if (Files.isSymbolicLink(directory)
              || !Files.getPosixFilePermissions(directory)
                  .equals(PosixFilePermissions.fromString("rwx------"))) throw busy();
          path =
              Files.createTempFile(
                  directory,
                  "received-",
                  ".tmp",
                  PosixFilePermissions.asFileAttribute(
                      PosixFilePermissions.fromString("rw-------")));
          output = Files.newOutputStream(path);
          input = request.getInputStream();
        }
        input.setReadListener(
            new ReadListener() {
              private final byte[] buffer = new byte[8192];

              @Override
              public void onDataAvailable() {
                synchronized (Admission.this) {
                  if (closed || result.isDone()) return;
                  try {
                    while (input.isReady() && !input.isFinished()) {
                      int count =
                          input.read(
                              buffer,
                              0,
                              (int)
                                  Math.min(buffer.length, settings.getMaxFileBytes() - bytes + 1));
                      if (count <= 0) break;
                      bytes += count;
                      if (bytes > settings.getMaxFileBytes()) throw tooLarge();
                      digest.update(buffer, 0, count);
                      output.write(buffer, 0, count);
                    }
                  } catch (IOException exception) {
                    fail(busy());
                  } catch (RuntimeException exception) {
                    fail(exception instanceof UploadInputException ? exception : busy());
                  }
                }
              }

              @Override
              public void onAllDataRead() {
                synchronized (Admission.this) {
                  if (closed || result.isDone()) return;
                  try {
                    if (bytes == 0)
                      throw new UploadInputException(
                          UploadInputException.Reason.INVALID, "空の画像は登録できません");
                    output.close();
                    output = null;
                    if (timeout != null) timeout.cancel(false);
                    result.complete(
                        new Received(path, HexFormat.of().formatHex(digest.digest()), mediaType));
                  } catch (IOException exception) {
                    fail(busy());
                  } catch (RuntimeException exception) {
                    fail(exception instanceof UploadInputException ? exception : busy());
                  }
                }
              }

              @Override
              public void onError(Throwable failure) {
                fail(busy());
              }
            });
        return result.get();
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        fail(busy());
        throw busy();
      } catch (ExecutionException exception) {
        // 非同期の失敗通知後も、ファイルと受信枠の解放が完了するまで待つ。
        close();
        if (exception.getCause() instanceof RuntimeException failure) throw failure;
        throw busy();
      } catch (IOException | NoSuchAlgorithmException exception) {
        fail(busy());
        throw busy();
      } catch (RuntimeException exception) {
        fail(exception instanceof UploadInputException ? exception : busy());
        throw exception instanceof UploadInputException ? exception : busy();
      }
    }

    private synchronized void fail(RuntimeException exception) {
      if (!result.isDone()) result.completeExceptionally(exception);
      close();
    }

    @Override
    public synchronized void close() {
      if (closed) return;
      closed = true;
      if (timeout != null) timeout.cancel(false);
      if (!result.isDone()) result.completeExceptionally(busy());
      try {
        if (output != null) output.close();
      } catch (IOException ignored) {
        // 一時ファイルはこの操作の所有物として削除を試み、枠は必ず解放する。
      } finally {
        try {
          if (path != null) Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
        permits.release();
      }
    }
  }

  public record Received(Path path, String originalSha256, String mediaType) {}

  private static UploadInputException tooLarge() {
    return new UploadInputException(UploadInputException.Reason.LIMIT, "画像のファイル容量が上限を超えています");
  }

  private static ServiceUnavailableException busy() {
    return new ServiceUnavailableException("画像を受信できません。時間をおいて再試行してください");
  }
}
