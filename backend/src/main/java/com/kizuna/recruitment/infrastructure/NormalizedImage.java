package com.kizuna.recruitment.infrastructure;

import java.io.IOException;
import java.nio.file.Path;

public record NormalizedImage(
    PrivateAttachmentFile file,
    String originalSha256,
    String canonicalSha256,
    String mediaType,
    long sizeBytes,
    String normalizerVersion)
    implements AutoCloseable {
  public Path path() {
    return file.path();
  }

  @Override
  public void close() throws IOException {
    file.close();
  }
}
