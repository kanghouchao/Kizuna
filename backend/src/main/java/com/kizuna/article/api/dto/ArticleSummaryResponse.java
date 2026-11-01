package com.kizuna.article.api.dto;

import com.kizuna.article.domain.ArticleSummaryView;
import com.kizuna.shared.publication.Publication;
import java.time.OffsetDateTime;

public record ArticleSummaryResponse(
    String id,
    String title,
    String coverImageUrl,
    Publication publication,
    boolean displayed,
    OffsetDateTime updatedAt,
    Long version) {
  public static ArticleSummaryResponse of(ArticleSummaryView article, OffsetDateTime now) {
    return new ArticleSummaryResponse(
        article.getId(),
        article.getTitle(),
        article.getCoverImageUrl(),
        article.getPublication(),
        article.getPublication().shownAt(now),
        article.getUpdatedAt(),
        article.getVersion());
  }
}
