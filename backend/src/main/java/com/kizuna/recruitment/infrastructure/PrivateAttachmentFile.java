package com.kizuna.recruitment.infrastructure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PrivateAttachmentFile implements AutoCloseable {
  private final Path path;
  private final Runnable release;
  private final AtomicBoolean closed = new AtomicBoolean();

  PrivateAttachmentFile(Path path, Runnable release) {
    this.path = path;
    this.release = release;
  }

  public Path path() {
    if (closed.get()) throw new IllegalStateException("添付の一時ファイルは利用終了済みです");
    return path;
  }

  @Override
  public void close() throws IOException {
    if (!closed.compareAndSet(false, true)) return;
    try {
      Files.deleteIfExists(path);
    } finally {
      release.run();
    }
  }
}
