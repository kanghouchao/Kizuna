package com.kizuna.article.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ArticleRepository extends StoreScopedRepository<Article> {
  Page<ArticleSummaryView> findByStoreId(Long storeId, Pageable pageable);
}
