package com.kizuna.recruitment.infrastructure;

import java.util.Objects;
import java.util.UUID;

public record AttachmentObject(UUID id, String mediaType, long sizeBytes, String sha256) {
  public AttachmentObject {
    Objects.requireNonNull(id);
    if (!("image/png".equals(mediaType) || "image/jpeg".equals(mediaType))
        || sizeBytes <= 0
        || sha256 == null
        || !sha256.matches("[a-f0-9]{64}")) {
      throw new IllegalArgumentException("添付の保存情報が不正です");
    }
  }
}
