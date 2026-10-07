package com.kizuna.recruitment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.ServiceUnavailableException;
import com.kizuna.shared.exception.UploadInputException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import javax.imageio.IIOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RasterImageNormalizerTest {
  @TempDir Path temporary;

  @Test
  void outputIoFailureIsUnavailableButTruncatedJpegRemainsInvalidInput() throws Exception {
    Path source = temporary.resolve("source");
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "jpeg", source.toFile());
    var settings = new AppProperties.PrivateAttachments();
    settings.setTemporaryDirectory(temporary.toString());
    settings.setMaxConcurrentUploads(1);
    var normalizer = new RasterImageNormalizer(settings);
    var writer = mock(ImageWriter.class);
    doThrow(new IIOException("合成された出力I/O障害"))
        .when(writer)
        .write(isNull(), any(IIOImage.class), isNull());
    try (var imageIo = mockStatic(ImageIO.class, CALLS_REAL_METHODS)) {
      imageIo
          .when(() -> ImageIO.getImageWritersByFormatName("jpeg"))
          .thenReturn(List.of(writer).iterator());
      assertThatThrownBy(() -> normalizer.normalize(source, "image/jpeg"))
          .isInstanceOf(ServiceUnavailableException.class)
          .hasNoCause();
    }
    try (var normalized = normalizer.normalize(source, "image/jpeg")) {
      assertThat(normalized.sizeBytes()).isPositive();
    }
    Files.write(source, Arrays.copyOf(Files.readAllBytes(source), 20));
    assertThatThrownBy(() -> normalizer.normalize(source, "image/jpeg"))
        .isInstanceOf(UploadInputException.class);
    try (var files = Files.list(temporary)) {
      assertThat(files.toList()).containsExactly(source);
    }
  }

  @Test
  void temporaryFilesystemFailuresAreUnavailableAndReleaseProcessingSlots() throws Exception {
    Path source = temporary.resolve("source");
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "jpeg", source.toFile());
    Path blockedDirectory = Files.writeString(temporary.resolve("not-a-directory"), "synthetic");
    var settings = new AppProperties.PrivateAttachments();
    settings.setMaxConcurrentUploads(1);
    settings.setTemporaryDirectory(blockedDirectory.toString());
    var normalizer = new RasterImageNormalizer(settings);
    assertThatThrownBy(() -> normalizer.normalize(source, "image/jpeg"))
        .isInstanceOf(ServiceUnavailableException.class)
        .hasNoCause();
    settings.setTemporaryDirectory(temporary.toString());
    try (var normalized = normalizer.normalize(source, "image/jpeg")) {
      assertThat(normalized.sizeBytes()).isPositive();
    }
    assertThatThrownBy(() -> normalizer.normalize(temporary.resolve("missing"), "image/jpeg"))
        .isInstanceOf(ServiceUnavailableException.class)
        .hasNoCause();
  }

  @Test
  void ordinaryPngIsReadableAndOriginalAndCanonicalDigestsAreIndependent() throws Exception {
    BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
    image.setRGB(0, 0, 0xffff0000);
    Path source = temporary.resolve("source");
    ImageIO.write(image, "png", source.toFile());
    var settings = new AppProperties.PrivateAttachments();
    settings.setTemporaryDirectory(temporary.toString());
    try (var normalized = new RasterImageNormalizer(settings).normalize(source, "image/png")) {
      BufferedImage decoded = ImageIO.read(normalized.path().toFile());
      assertThat(decoded.getRGB(0, 0)).isEqualTo(0xffff0000);
      assertThat(normalized.originalSha256()).hasSize(64);
      assertThat(normalized.canonicalSha256()).hasSize(64);
      assertThat(normalized.sizeBytes()).isEqualTo(Files.size(normalized.path()));
    }
    try (var files = Files.list(temporary)) {
      assertThat(files.toList()).containsExactly(source);
    }
  }

  @Test
  void dimensionBombIsRejectedWithoutAllocatingTheDeclaredRaster() throws Exception {
    Path source = temporary.resolve("bomb");
    try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(source))) {
      output.write(new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10});
      ByteArrayOutputStream header = new ByteArrayOutputStream();
      try (DataOutputStream data = new DataOutputStream(header)) {
        data.writeInt(100_000);
        data.writeInt(100_000);
        data.write(new byte[] {8, 6, 0, 0, 0});
      }
      writeChunk(output, "IHDR", header.toByteArray());
      writeChunk(output, "IEND", new byte[0]);
    }
    var settings = new AppProperties.PrivateAttachments();
    settings.setTemporaryDirectory(temporary.toString());
    assertThatThrownBy(() -> new RasterImageNormalizer(settings).normalize(source, "image/png"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("画素");
  }

  @Test
  void activeTextDisguisedAsPngAndTruncatedImagesAreRejected() throws Exception {
    Path source = Files.writeString(temporary.resolve("fake"), "<svg onload='alert(1)'/>");
    var settings = new AppProperties.PrivateAttachments();
    settings.setTemporaryDirectory(temporary.toString());
    var normalizer = new RasterImageNormalizer(settings);
    assertThatThrownBy(() -> normalizer.normalize(source, "image/png"))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> normalizer.normalize(source, "image/svg+xml"))
        .isInstanceOf(ServiceException.class);
    Files.write(source, new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10});
    assertThatThrownBy(() -> normalizer.normalize(source, "image/png"))
        .isInstanceOf(ServiceException.class);
  }

  @Test
  void compressedPayloadCannotExpandBeyondItsDeclaredPixels() throws Exception {
    Path source = temporary.resolve("compressed-bomb");
    ByteArrayOutputStream compressed = new ByteArrayOutputStream();
    try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed)) {
      for (int i = 0; i < 100; i++) deflater.write(new byte[8192]);
    }
    try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(source))) {
      output.write(new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10});
      ByteArrayOutputStream header = new ByteArrayOutputStream();
      try (DataOutputStream data = new DataOutputStream(header)) {
        data.writeInt(1);
        data.writeInt(1);
        data.write(new byte[] {8, 6, 0, 0, 0});
      }
      writeChunk(output, "IHDR", header.toByteArray());
      writeChunk(output, "IDAT", compressed.toByteArray());
      writeChunk(output, "IEND", new byte[0]);
    }
    var settings = new AppProperties.PrivateAttachments();
    settings.setTemporaryDirectory(temporary.toString());
    assertThatThrownBy(() -> new RasterImageNormalizer(settings).normalize(source, "image/png"))
        .isInstanceOf(ServiceException.class);
    try (var files = Files.list(temporary)) {
      assertThat(files.toList()).containsExactly(source);
    }
  }

  @Test
  void animationIsRejectedAndAncillaryTextIsNotCopied() throws Exception {
    Path source = temporary.resolve("source");
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB), "png", source.toFile());
    byte[] original = Files.readAllBytes(source);
    ByteArrayOutputStream animated = new ByteArrayOutputStream();
    try (DataOutputStream output = new DataOutputStream(animated)) {
      output.write(original, 0, 33);
      writeChunk(output, "acTL", new byte[] {0, 0, 0, 2, 0, 0, 0, 0});
      output.write(original, 33, original.length - 33);
    }
    var settings = new AppProperties.PrivateAttachments();
    settings.setTemporaryDirectory(temporary.toString());
    var normalizer = new RasterImageNormalizer(settings);
    Files.write(source, animated.toByteArray());
    assertThatThrownBy(() -> normalizer.normalize(source, "image/png"))
        .isInstanceOf(ServiceException.class);
    ByteArrayOutputStream annotated = new ByteArrayOutputStream();
    try (DataOutputStream output = new DataOutputStream(annotated)) {
      output.write(original, 0, 33);
      writeChunk(
          output, "tEXt", "Comment\0synthetic-private-note".getBytes(StandardCharsets.US_ASCII));
      output.write(original, 33, original.length - 33);
    }
    Files.write(source, annotated.toByteArray());
    try (var normalized = normalizer.normalize(source, "image/png")) {
      assertThat(new String(Files.readAllBytes(normalized.path()), StandardCharsets.ISO_8859_1))
          .doesNotContain("synthetic-private-note");
      assertThat(normalized.canonicalSha256()).isNotEqualTo(normalized.originalSha256());
    }
  }

  @Test
  void decodedMemoryBudgetAppliesEvenWhenPixelCountIsAllowed() throws Exception {
    Path source = temporary.resolve("source");
    ImageIO.write(new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB), "png", source.toFile());
    var settings = new AppProperties.PrivateAttachments();
    settings.setTemporaryDirectory(temporary.toString());
    settings.setMaxDecodedBytes(1024);
    assertThatThrownBy(() -> new RasterImageNormalizer(settings).normalize(source, "image/png"))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("デコード");
  }

  @Test
  void ordinaryJpegIsNormalizedAndMimeMismatchIsRejected() throws Exception {
    Path source = temporary.resolve("source");
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "jpeg", source.toFile());
    var settings = new AppProperties.PrivateAttachments();
    settings.setTemporaryDirectory(temporary.toString());
    var normalizer = new RasterImageNormalizer(settings);
    assertThatThrownBy(() -> normalizer.normalize(source, "image/png"))
        .isInstanceOf(ServiceException.class);
    try (var normalized = normalizer.normalize(source, "image/jpeg")) {
      assertThat(ImageIO.read(normalized.path().toFile()).getWidth()).isEqualTo(2);
    }
  }

  static void writeChunk(DataOutputStream out, String type, byte[] bytes) throws Exception {
    byte[] name = type.getBytes(StandardCharsets.US_ASCII);
    out.writeInt(bytes.length);
    out.write(name);
    out.write(bytes);
    CRC32 crc = new CRC32();
    crc.update(name);
    crc.update(bytes);
    out.writeInt((int) crc.getValue());
  }
}
