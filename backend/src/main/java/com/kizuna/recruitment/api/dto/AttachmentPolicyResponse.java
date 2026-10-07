package com.kizuna.recruitment.api.dto;

import java.util.List;

public record AttachmentPolicyResponse(
    boolean configured,
    List<String> allowedMediaTypes,
    long maxFileBytes,
    long maxImagePixels,
    int maxImageDimension,
    long maxDecodedBytes,
    int maxApplicantFiles,
    long maxApplicantBytes) {}
