package com.kizuna.article.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import com.kizuna.shared.publication.Publication;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ArticleRepository
    extends StoreScopedRepository<Article>, JpaSpecificationExecutor<Article> {
  Page<ArticleSummaryView> findByStoreId(Long storeId, Pageable pageable);

  /** 店舗を絞った、いま公式サイトに掲出中の記事。記事は公開設定のほかに前提を持たない。 */
  static Specification<Article> displayed(Long storeId, OffsetDateTime at) {
    Specification<Article> store = (root, q, cb) -> cb.equal(root.get("storeId"), storeId);
    return store.and(Publication.shownSpecification(Article::getPublication, at));
  }
}
