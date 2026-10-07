package com.kizuna.recruitment.infrastructure;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ResourceBusyException;
import com.kizuna.shared.exception.ServiceUnavailableException;
import com.kizuna.shared.exception.UploadInputException;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.concurrent.Semaphore;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.event.IIOReadProgressListener;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.FileImageOutputStream;

public class RasterImageNormalizer {
  public static final String VERSION = "raster-v1-jdk25";
  private final AppProperties.PrivateAttachments settings;
  private final Semaphore permits;

  public RasterImageNormalizer(AppProperties.PrivateAttachments settings) {
    this.settings = settings;
    if (settings.getMaxConcurrentUploads() < 1
        || settings.getMaxConcurrentUploads() > 2
        || settings.getMaxDecodedBytes() < 1
        || settings.getMaxDecodedBytes() > 64L * 1024 * 1024
        || settings.getImageProcessingTimeoutSeconds() < 1
        || settings.getImageProcessingTimeoutSeconds() > 15
        || settings.getMaxImagePixels() < 1
        || settings.getMaxImagePixels() > 16_777_216
        || settings.getMaxImageDimension() < 1
        || settings.getMaxImageDimension() > 8192
        || settings.getMaxFileBytes() < 1
        || settings.getMaxFileBytes() > 10L * 1024 * 1024) {
      throw new IllegalArgumentException("非公開画像の制限設定が不正です");
    }
    permits = new Semaphore(settings.getMaxConcurrentUploads());
  }

  public NormalizedImage normalize(Path source, String mediaType) {
    String format =
        switch (mediaType == null ? "" : mediaType) {
          case "image/png" -> "png";
          case "image/jpeg" -> "jpeg";
          default ->
              throw new UploadInputException(
                  UploadInputException.Reason.TYPE, "JPEGまたはPNG画像を選択してください");
        };
    if (!permits.tryAcquire()) throw new ResourceBusyException("画像処理が混み合っています。再試行してください");
    long deadline =
        System.nanoTime()
            + Duration.ofSeconds(settings.getImageProcessingTimeoutSeconds()).toNanos();
    Path filtered = null;
    Path compressed = null;
    Path output = null;
    boolean transferred = false;
    try {
      long originalSize = Files.size(source);
      if (originalSize <= 0) throw invalid();
      if (originalSize > settings.getMaxFileBytes()) throw limit();
      String originalHash = sha256(source);
      Path directory = Path.of(settings.getTemporaryDirectory());
      Files.createDirectories(
          directory,
          PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
      if (Files.isSymbolicLink(directory)
          || !Files.getPosixFilePermissions(directory)
              .equals(PosixFilePermissions.fromString("rwx------"))) throw invalid();
      Path raster = source;
      if (format.equals("png")) {
        filtered = temporary(directory);
        compressed = temporary(directory);
        PngRasterSource.prepare(
            source,
            filtered,
            compressed,
            settings.getMaxFileBytes(),
            settings.getMaxImagePixels(),
            settings.getMaxImageDimension(),
            settings.getMaxDecodedBytes(),
            deadline);
        raster = filtered;
      }
      try (FileImageInputStream input = new FileImageInputStream(raster.toFile())) {
        Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext()) throw invalid();
        ImageReader reader = readers.next();
        try {
          if (!reader.getFormatName().equalsIgnoreCase(format)) throw invalid();
          reader.setInput(input, false, true);
          requireDimensions(
              reader.getWidth(0),
              reader.getHeight(0),
              settings.getMaxImagePixels(),
              settings.getMaxImageDimension());
          if (reader.getNumImages(true) != 1) throw invalid();
          reader.addIIOReadWarningListener(
              (ignored, warning) -> {
                throw invalid();
              });
          ImageTypeSpecifier imageType = reader.getImageTypes(0).next();
          long bytesPerPixel =
              (long) imageType.getSampleModel().getNumDataElements()
                  * DataBuffer.getDataTypeSize(imageType.getSampleModel().getDataType())
                  / 8;
          requireDecodedBytes(
              reader.getWidth(0),
              reader.getHeight(0),
              Math.max(1, bytesPerPixel),
              settings.getMaxDecodedBytes());
          reader.addIIOReadProgressListener(
              new IIOReadProgressListener() {
                public void sequenceStarted(ImageReader source, int index) {
                  requireTime(deadline);
                }

                public void sequenceComplete(ImageReader source) {
                  requireTime(deadline);
                }

                public void imageStarted(ImageReader source, int index) {
                  requireTime(deadline);
                }

                public void imageProgress(ImageReader source, float percent) {
                  requireTime(deadline);
                }

                public void imageComplete(ImageReader source) {
                  requireTime(deadline);
                }

                public void thumbnailStarted(ImageReader source, int index, int thumbnail) {
                  throw invalid();
                }

                public void thumbnailProgress(ImageReader source, float percent) {
                  throw invalid();
                }

                public void thumbnailComplete(ImageReader source) {
                  throw invalid();
                }

                public void readAborted(ImageReader source) {
                  throw invalid();
                }
              });
          BufferedImage image = reader.read(0);
          try {
            output = temporary(directory);
            encode(image, format, output, settings.getMaxFileBytes(), deadline);
          } finally {
            image.flush();
          }
        } finally {
          reader.dispose();
        }
      }
      requireTime(deadline);
      if (Files.size(output) > settings.getMaxFileBytes()) throw limit();
      NormalizedImage result =
          new NormalizedImage(
              new PrivateAttachmentFile(output, permits::release),
              originalHash,
              sha256(output),
              mediaType,
              Files.size(output),
              VERSION);
      transferred = true;
      return result;
    } catch (IOException exception) {
      throw invalid();
    } finally {
      try {
        if (filtered != null) Files.deleteIfExists(filtered);
        if (compressed != null) Files.deleteIfExists(compressed);
        if (!transferred && output != null) Files.deleteIfExists(output);
      } catch (IOException exception) {
        if (transferred) {
          try {
            Files.deleteIfExists(output);
          } catch (IOException ignored) {
          }
          permits.release();
        }
        throw new ServiceUnavailableException("画像の一時領域を利用できません");
      } finally {
        if (!transferred) permits.release();
      }
    }
  }

  static void requireDimensions(int width, int height, long maxPixels, int maxDimension) {
    if (width <= 0 || height <= 0) throw invalid();
    if (width > maxDimension || height > maxDimension || (long) width * height > maxPixels) {
      throw new UploadInputException(UploadInputException.Reason.LIMIT, "画像の寸法または画素数が上限を超えています");
    }
  }

  static void requireDecodedBytes(int width, int height, long bytesPerPixel, long maximum) {
    if ((long) width * height * bytesPerPixel > maximum) {
      throw new UploadInputException(UploadInputException.Reason.LIMIT, "画像のデコード容量が上限を超えています");
    }
  }

  static void requireTime(long deadline) {
    if (System.nanoTime() > deadline || Thread.currentThread().isInterrupted()) {
      throw new ServiceUnavailableException("画像処理が制限時間を超えました");
    }
  }

  private static void encode(
      BufferedImage image, String format, Path output, long maxBytes, long deadline)
      throws IOException {
    ImageWriter writer = ImageIO.getImageWritersByFormatName(format).next();
    try (FileImageOutputStream stream =
        new FileImageOutputStream(output.toFile()) {
          @Override
          public void write(int value) throws IOException {
            requireTime(deadline);
            if (getStreamPosition() + 1 > maxBytes) throw limit();
            super.write(value);
          }

          @Override
          public void write(byte[] bytes, int offset, int length) throws IOException {
            requireTime(deadline);
            if (getStreamPosition() + length > maxBytes) throw limit();
            super.write(bytes, offset, length);
          }

          @Override
          public void seek(long position) throws IOException {
            requireTime(deadline);
            if (position > maxBytes) throw limit();
            super.seek(position);
          }
        }) {
      writer.setOutput(stream);
      writer.write(null, new IIOImage(image, null, null), writer.getDefaultWriteParam());
    } finally {
      writer.dispose();
    }
  }

  private static Path temporary(Path directory) throws IOException {
    return Files.createTempFile(
        directory,
        "image-",
        ".tmp",
        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
  }

  private static String sha256(Path file) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (InputStream input = Files.newInputStream(file)) {
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("画像の整合性検証が利用できません");
    }
  }

  private static UploadInputException invalid() {
    return new UploadInputException(UploadInputException.Reason.INVALID, "正常な単一画像を選択してください");
  }

  private static UploadInputException limit() {
    return new UploadInputException(UploadInputException.Reason.LIMIT, "画像のファイル容量が上限を超えています");
  }
}
