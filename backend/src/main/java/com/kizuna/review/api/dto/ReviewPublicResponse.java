package com.kizuna.review.api.dto;

import com.kizuna.review.domain.ReviewPublicView;
import java.time.LocalDate;
import java.time.ZoneId;

public record ReviewPublicResponse(String displayName, String body, LocalDate receivedOn) {
  public static ReviewPublicResponse of(ReviewPublicView r, ZoneId zone) {
    return new ReviewPublicResponse(
        r.getDisplayName(), r.getBody(), r.getReceivedAt().atZoneSameInstant(zone).toLocalDate());
  }
}
