package com.kizuna.recruitment.api.dto;

import com.kizuna.recruitment.application.AttachmentViews;
import com.kizuna.recruitment.domain.AttachmentUpload;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AttachmentUploadResponse(
    String id,
    UUID idempotencyKey,
    AttachmentUpload.Status status,
    String mediaType,
    long sizeBytes,
    OffsetDateTime createdAt,
    AttachmentUpload.Failure failureCode) {
  public static AttachmentUploadResponse from(AttachmentViews.Upload upload) {
    return new AttachmentUploadResponse(
        upload.id(),
        upload.idempotencyKey(),
        upload.status(),
        upload.mediaType(),
        upload.sizeBytes(),
        upload.createdAt(),
        upload.failureCode());
  }
}
