package com.kizuna.review.api.dto;

import com.kizuna.review.application.ReviewViews;
import java.time.LocalDate;

public record ReviewPublicResponse(String displayName, String body, LocalDate receivedOn) {
  public static ReviewPublicResponse of(ReviewViews.PublicEntry entry) {
    return new ReviewPublicResponse(entry.displayName(), entry.body(), entry.receivedOn());
  }
}
