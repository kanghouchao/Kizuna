package com.kizuna.recruitment.api.dto;

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
  public static AttachmentUploadResponse from(AttachmentUpload upload) {
    return new AttachmentUploadResponse(
        upload.getId(),
        upload.getIdempotencyKey(),
        upload.getStatus(),
        upload.getMediaType(),
        upload.getSizeBytes(),
        upload.getCreatedAt(),
        upload.getFailure());
  }
}
