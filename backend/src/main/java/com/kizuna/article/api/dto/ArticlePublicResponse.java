package com.kizuna.article.api.dto;

import com.kizuna.article.domain.ArticlePublicView;
import java.time.OffsetDateTime;

public record ArticlePublicResponse(
    String id, String title, OffsetDateTime publishFrom, String body) {
  public static ArticlePublicResponse of(ArticlePublicView a) {
    return new ArticlePublicResponse(
        a.getId(), a.getTitle(), a.getPublication().publishFrom(), a.getBody());
  }
}
