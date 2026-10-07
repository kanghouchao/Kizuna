package com.kizuna.recruitment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ResourceBusyException;
import com.kizuna.shared.exception.ServiceUnavailableException;
import com.kizuna.shared.exception.UploadInputException;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AttachmentBodyReceiverTest {
  @TempDir Path directory;

  @Test
  void chunkedBodyIsBoundedBeforeHashAndWrittenBytesAndReleasesSlot() throws Exception {
    var settings = properties();
    settings.getPrivateAttachments().setMaxFileBytes(4);
    var stream = new Body(new byte[100], false);
    try (var receiver = new AttachmentBodyReceiver(settings)) {
      var request = request(stream, -1, "image/png");
      try (var admission = receiver.admit(request)) {
        assertThatThrownBy(() -> admission.receive(request))
            .isInstanceOf(UploadInputException.class);
      }
      assertThat(stream.position).isEqualTo(5);
      assertEmpty();
      try (var next = receiver.admit(request(new Body(new byte[] {1}, false), 1, "image/png"))) {
        assertThat(next).isNotNull();
      }
    }
  }

  @Test
  void originalDigestMatchesEntireBodyAndTemporaryFileHasPrivateMode() throws Exception {
    byte[] bytes = {1, 2, 3, 4};
    var request = request(new Body(bytes, false), -1, "image/jpeg");
    try (var receiver = new AttachmentBodyReceiver(properties());
        var admission = receiver.admit(request)) {
      var received = admission.receive(request);
      assertThat(received.originalSha256())
          .isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
      assertThat(Files.readAllBytes(received.path())).containsExactly(bytes);
      assertThat(Files.getPosixFilePermissions(received.path()))
          .containsExactlyInAnyOrder(
              PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
      assertThatThrownBy(() -> receiver.admit(request)).isInstanceOf(ResourceBusyException.class);
    }
    assertEmpty();
  }

  @Test
  void stalledNonblockingBodyExpiresAndReleasesTemporaryFileAndAdmission() throws Exception {
    var request = request(new Body(new byte[] {1}, true), -1, "image/png");
    long start = System.nanoTime();
    try (var receiver = new AttachmentBodyReceiver(properties());
        var admission = receiver.admit(request)) {
      assertThatThrownBy(() -> admission.receive(request))
          .isInstanceOf(ServiceUnavailableException.class);
      assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
      assertEmpty();
      try (var next = receiver.admit(request)) {
        assertThat(next).isNotNull();
      }
    }
  }

  @Test
  void declaredOversizeAndUnsupportedMediaNeverReadBody() throws Exception {
    var stream = new Body(new byte[] {1}, false);
    try (var receiver = new AttachmentBodyReceiver(properties())) {
      assertThatThrownBy(() -> receiver.admit(request(stream, 11L * 1024 * 1024, "image/png")))
          .isInstanceOf(UploadInputException.class);
      assertThatThrownBy(() -> receiver.admit(request(stream, 1, "image/svg+xml")))
          .isInstanceOf(UploadInputException.class);
      assertThat(stream.position).isZero();
    }
  }

  private AppProperties properties() {
    var properties = new AppProperties();
    properties
        .getPrivateAttachments()
        .setTemporaryDirectory(directory.resolve("private").toString());
    properties.getPrivateAttachments().setReceiveTimeoutSeconds(1);
    properties.getPrivateAttachments().setMaxConcurrentUploads(1);
    return properties;
  }

  private void assertEmpty() throws IOException {
    var privateDirectory = directory.resolve("private");
    if (Files.exists(privateDirectory))
      try (var paths = Files.list(privateDirectory)) {
        assertThat(paths).isEmpty();
      }
  }

  private static HttpServletRequest request(ServletInputStream stream, long size, String type)
      throws IOException {
    var request = mock(HttpServletRequest.class);
    when(request.getContentType()).thenReturn(type);
    when(request.getContentLengthLong()).thenReturn(size);
    when(request.getInputStream()).thenReturn(stream);
    return request;
  }

  private static class Body extends ServletInputStream {
    private final byte[] bytes;
    private final boolean stalled;
    private int position;

    Body(byte[] bytes, boolean stalled) {
      this.bytes = bytes;
      this.stalled = stalled;
    }

    @Override
    public boolean isFinished() {
      return position == bytes.length;
    }

    @Override
    public boolean isReady() {
      return !stalled;
    }

    @Override
    public int read() {
      return isFinished() ? -1 : bytes[position++] & 255;
    }

    @Override
    public void setReadListener(ReadListener listener) {
      if (stalled) return;
      try {
        listener.onDataAvailable();
        if (isFinished()) listener.onAllDataRead();
      } catch (IOException exception) {
        listener.onError(exception);
      }
    }
  }
}
