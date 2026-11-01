package com.kizuna.article.domain;

import com.kizuna.shared.publication.Publication;

/** 公式サイトの記事一覧の投影。本文を読まない。 */
public interface ArticlePublicSummaryView {
  String getId();

  String getTitle();

  Publication getPublication();
}
