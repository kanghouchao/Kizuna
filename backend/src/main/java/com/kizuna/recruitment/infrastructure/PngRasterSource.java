package com.kizuna.recruitment.infrastructure;

import com.kizuna.shared.exception.UploadInputException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

final class PngRasterSource {
  private static final byte[] SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
  private static final Set<String> RASTER_CHUNKS = Set.of("IHDR", "PLTE", "tRNS", "IDAT", "IEND");

  private PngRasterSource() {}

  static void prepare(
      Path source,
      Path filtered,
      Path compressed,
      long maxBytes,
      long maxPixels,
      int maxDimension,
      long maxDecodedBytes,
      long deadline)
      throws IOException {
    long expandedBytes = 0;
    boolean headerSeen = false;
    boolean dataSeen = false;
    try (DataInputStream input = new DataInputStream(Files.newInputStream(source));
        DataOutputStream output = new DataOutputStream(Files.newOutputStream(filtered));
        OutputStream idat = Files.newOutputStream(compressed)) {
      if (!Arrays.equals(input.readNBytes(8), SIGNATURE)) throw invalid();
      output.write(SIGNATURE);
      byte[] buffer = new byte[8192];
      while (true) {
        RasterImageNormalizer.requireTime(deadline);
        int length = input.readInt();
        if (length < 0 || length > maxBytes) throw invalid();
        byte[] typeBytes = input.readNBytes(4);
        if (typeBytes.length != 4) throw invalid();
        String type = new String(typeBytes, StandardCharsets.US_ASCII);
        if (!type.matches("[a-zA-Z]{4}") || Set.of("acTL", "fcTL", "fdAT").contains(type))
          throw invalid();
        if (!headerSeen && !type.equals("IHDR")) throw invalid();
        boolean retain = RASTER_CHUNKS.contains(type);
        if (!retain && Character.isUpperCase(type.charAt(0))) throw invalid();
        if (retain) {
          output.writeInt(length);
          output.write(typeBytes);
        }
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        if (type.equals("IHDR")) {
          if (headerSeen || length != 13) throw invalid();
          byte[] header = input.readNBytes(13);
          if (header.length != 13) throw invalid();
          expandedBytes = expandedSize(header, maxPixels, maxDimension, maxDecodedBytes);
          headerSeen = true;
          crc.update(header);
          output.write(header);
        } else {
          int remaining = length;
          while (remaining > 0) {
            RasterImageNormalizer.requireTime(deadline);
            int count = input.read(buffer, 0, Math.min(buffer.length, remaining));
            if (count < 0) throw invalid();
            remaining -= count;
            crc.update(buffer, 0, count);
            if (retain) output.write(buffer, 0, count);
            if (type.equals("IDAT")) idat.write(buffer, 0, count);
          }
          if (type.equals("IDAT")) dataSeen = true;
        }
        int checksum = input.readInt();
        if ((int) crc.getValue() != checksum) throw invalid();
        if (retain) output.writeInt(checksum);
        if (type.equals("IEND")) {
          if (length != 0 || !dataSeen || input.read() != -1) throw invalid();
          break;
        }
      }
    }
    verifyInflatedSize(compressed, expandedBytes, deadline);
  }

  private static long expandedSize(
      byte[] header, long maxPixels, int maxDimension, long maxDecodedBytes) {
    ByteBuffer data = ByteBuffer.wrap(header);
    int width = data.getInt();
    int height = data.getInt();
    RasterImageNormalizer.requireDimensions(width, height, maxPixels, maxDimension);
    int depth = Byte.toUnsignedInt(data.get());
    int color = Byte.toUnsignedInt(data.get());
    int samples =
        switch (color) {
          case 0, 3 -> 1;
          case 2 -> 3;
          case 4 -> 2;
          case 6 -> 4;
          default -> 0;
        };
    int compression = Byte.toUnsignedInt(data.get());
    int filter = Byte.toUnsignedInt(data.get());
    int interlace = Byte.toUnsignedInt(data.get());
    if (!Set.of(1, 2, 4, 8, 16).contains(depth)
        || samples == 0
        || compression != 0
        || filter != 0
        || interlace > 1) throw invalid();
    long bits = (long) samples * depth;
    RasterImageNormalizer.requireDecodedBytes(
        width, height, Math.max(1, (bits + 7) / 8), maxDecodedBytes);
    if (interlace == 0) return rows(width, height, bits);
    int[] x = {0, 4, 0, 2, 0, 1, 0};
    int[] y = {0, 0, 4, 0, 2, 0, 1};
    int[] dx = {8, 8, 4, 4, 2, 2, 1};
    int[] dy = {8, 8, 8, 4, 4, 2, 2};
    long total = 0;
    for (int i = 0; i < 7; i++) {
      int passWidth = Math.max(0, (width - x[i] + dx[i] - 1) / dx[i]);
      int passHeight = Math.max(0, (height - y[i] + dy[i] - 1) / dy[i]);
      if (passWidth > 0 && passHeight > 0) total += rows(passWidth, passHeight, bits);
    }
    return total;
  }

  private static long rows(int width, int height, long bits) {
    return ((width * bits + 7) / 8 + 1) * height;
  }

  private static void verifyInflatedSize(Path compressed, long expected, long deadline)
      throws IOException {
    Inflater inflater = new Inflater();
    try (InputStream source = Files.newInputStream(compressed);
        InflaterInputStream input = new InflaterInputStream(source, inflater)) {
      byte[] buffer = new byte[8192];
      long total = 0;
      int count;
      while ((count = input.read(buffer, 0, (int) Math.min(buffer.length, expected - total + 1)))
          != -1) {
        RasterImageNormalizer.requireTime(deadline);
        total += count;
        if (total > expected) throw invalid();
      }
      if (total != expected
          || !inflater.finished()
          || inflater.getRemaining() != 0
          || source.read() != -1) throw invalid();
    } finally {
      inflater.end();
    }
  }

  private static UploadInputException invalid() {
    return new UploadInputException(UploadInputException.Reason.INVALID, "単一の正常なPNG画像を選択してください");
  }
}
