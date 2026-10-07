package com.kizuna.recruitment.api.dto;

import com.kizuna.recruitment.domain.AttachmentUpload;
import java.time.OffsetDateTime;

public record AttachmentSummaryResponse(
    String id, String mediaType, long sizeBytes, OffsetDateTime createdAt) {
  public static AttachmentSummaryResponse from(AttachmentUpload upload) {
    return new AttachmentSummaryResponse(
        upload.getId(), upload.getMediaType(), upload.getSizeBytes(), upload.getCreatedAt());
  }
}
