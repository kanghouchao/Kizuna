package com.kizuna.article.domain;

import com.kizuna.shared.publication.Publication;
import java.time.OffsetDateTime;

/** 記事一覧の投影。本文を読まない。 */
public interface ArticleSummaryView {
  String getId();

  String getTitle();

  String getCoverImageUrl();

  Publication getPublication();

  OffsetDateTime getUpdatedAt();

  Long getVersion();
}
