package com.kizuna.article.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ArticleRepository
    extends StoreScopedRepository<Article>, JpaSpecificationExecutor<Article> {
  Page<ArticleSummaryView> findByStoreId(Long storeId, Pageable pageable);

  /** 公式サイトに載る前提。記事は店舗のほかに前提を持たず、掲出の判定は公開設定に任せる。 */
  static Specification<Article> prerequisites(Long storeId) {
    return (root, q, cb) -> cb.equal(root.get("storeId"), storeId);
  }
}
