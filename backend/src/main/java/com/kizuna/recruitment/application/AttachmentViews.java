package com.kizuna.recruitment.application;

import com.kizuna.recruitment.domain.AttachmentUpload;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** 応募者添付の読み取り結果。保存先のキーやハッシュは含めない。 */
public final class AttachmentViews {
  private AttachmentViews() {}

  public record Policy(
      boolean configured,
      List<String> allowedMediaTypes,
      long maxFileBytes,
      long maxImagePixels,
      int maxImageDimension,
      long maxDecodedBytes,
      int maxApplicantFiles,
      long maxApplicantBytes) {}

  public record Summary(String id, String mediaType, long sizeBytes, OffsetDateTime createdAt) {
    static Summary of(AttachmentUpload upload) {
      return new Summary(
          upload.getId(), upload.getMediaType(), upload.getSizeBytes(), upload.getCreatedAt());
    }
  }

  public record Upload(
      String id,
      UUID idempotencyKey,
      AttachmentUpload.Status status,
      String mediaType,
      long sizeBytes,
      OffsetDateTime createdAt,
      AttachmentUpload.Failure failureCode) {
    static Upload of(AttachmentUpload upload) {
      return new Upload(
          upload.getId(),
          upload.getIdempotencyKey(),
          upload.getStatus(),
          upload.getMediaType(),
          upload.getSizeBytes(),
          upload.getCreatedAt(),
          upload.getFailure());
    }
  }
}
