package com.kizuna.article.api.dto;

import com.kizuna.article.domain.ArticlePublicSummaryView;
import java.time.OffsetDateTime;

public record ArticlePublicSummaryResponse(String id, String title, OffsetDateTime publishFrom) {
  public static ArticlePublicSummaryResponse of(ArticlePublicSummaryView a) {
    return new ArticlePublicSummaryResponse(
        a.getId(), a.getTitle(), a.getPublication().publishFrom());
  }
}
