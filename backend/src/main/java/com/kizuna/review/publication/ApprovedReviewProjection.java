package com.kizuna.review.publication;

public record ApprovedReviewProjection(
    String reviewId, Long reviewVersion, String displayName, String body) {}
