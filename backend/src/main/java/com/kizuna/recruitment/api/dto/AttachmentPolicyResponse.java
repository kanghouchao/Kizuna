package com.kizuna.recruitment.api.dto;

import com.kizuna.recruitment.application.AttachmentViews;
import java.util.List;

public record AttachmentPolicyResponse(
    boolean configured,
    List<String> allowedMediaTypes,
    long maxFileBytes,
    long maxImagePixels,
    int maxImageDimension,
    long maxDecodedBytes,
    int maxApplicantFiles,
    long maxApplicantBytes) {
  public static AttachmentPolicyResponse from(AttachmentViews.Policy policy) {
    return new AttachmentPolicyResponse(
        policy.configured(),
        policy.allowedMediaTypes(),
        policy.maxFileBytes(),
        policy.maxImagePixels(),
        policy.maxImageDimension(),
        policy.maxDecodedBytes(),
        policy.maxApplicantFiles(),
        policy.maxApplicantBytes());
  }
}
