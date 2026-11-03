package com.kizuna.recruitment.api.dto;

import com.kizuna.recruitment.application.AttachmentViews;
import java.time.OffsetDateTime;

public record AttachmentSummaryResponse(
    String id, String mediaType, long sizeBytes, OffsetDateTime createdAt) {
  public static AttachmentSummaryResponse from(AttachmentViews.Summary attachment) {
    return new AttachmentSummaryResponse(
        attachment.id(), attachment.mediaType(), attachment.sizeBytes(), attachment.createdAt());
  }
}
