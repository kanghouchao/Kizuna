package com.kizuna.article.api.dto;

import com.kizuna.article.domain.Article;
import com.kizuna.shared.publication.Publication;
import java.time.OffsetDateTime;

public record ArticleResponse(
    String id,
    String title,
    String body,
    Publication publication,
    boolean displayed,
    OffsetDateTime updatedAt,
    Long version) {
  public static ArticleResponse of(Article article, OffsetDateTime now) {
    return new ArticleResponse(
        article.getId(),
        article.getTitle(),
        article.getBody(),
        article.getPublication(),
        article.getPublication().shownAt(now),
        article.getUpdatedAt(),
        article.getVersion());
  }
}
