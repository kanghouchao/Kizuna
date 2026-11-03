package com.kizuna.article.application;

import com.kizuna.article.domain.Article;
import com.kizuna.article.domain.ArticlePublicSummaryView;
import com.kizuna.article.domain.ArticlePublicView;
import com.kizuna.article.domain.ArticleRepository;
import com.kizuna.article.domain.ArticleSummaryView;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.publication.Publication;
import com.kizuna.user.application.BusinessAudit;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArticleService {
  private final ArticleRepository articles;
  private final BusinessAudit audit;
  private final Clock clock;

  public Page<ArticleSummaryView> list(Long storeId, Pageable pageable) {
    return articles.findByStoreId(storeId, pageable);
  }

  public Article get(Long storeId, String id) {
    return find(storeId, id);
  }

  public Page<ArticlePublicSummaryView> listPublic(Long storeId, Pageable pageable) {
    var listing =
        Publication.listing(
            Article::getPublication,
            Article::getId,
            ArticleRepository.prerequisites(storeId),
            OffsetDateTime.now(clock),
            pageable);
    return articles.findBy(
        listing.specification(),
        q -> q.as(ArticlePublicSummaryView.class).page(listing.pageable()));
  }

  /** 存在しない・他店・掲出外を同じ 404 にし、訪問者に掲出外の記事の存在を知らせない。 */
  public ArticlePublicView getPublic(Long storeId, String id) {
    Specification<Article> byId = (root, q, cb) -> cb.equal(root.get("id"), id);
    return articles
        .findBy(
            ArticleRepository.prerequisites(storeId)
                .and(
                    Publication.shownSpecification(
                        Article::getPublication, OffsetDateTime.now(clock)))
                .and(byId),
            q -> q.as(ArticlePublicView.class).first())
        .orElseThrow(() -> new NotFoundException("記事が見つかりません"));
  }

  @Transactional
  public String create(Long storeId, String title, String body, String coverImageUrl) {
    var article = articles.saveAndFlush(Article.write(storeId, title, body, coverImageUrl));
    record(storeId, "ARTICLE_CREATED", article, absent(), snapshot(article));
    return article.getId();
  }

  @Transactional
  public Article edit(
      Long storeId,
      String id,
      String title,
      String body,
      String coverImageUrl,
      long expectedVersion) {
    var article = find(storeId, id);
    article.requireVersion(expectedVersion);
    var before = snapshot(article);
    boolean bodyChanged = article.edit(title, body, coverImageUrl);
    articles.saveAndFlush(article);
    var after = snapshot(article);
    if (bodyChanged) after.put("redacted_fields_changed", "body");
    record(storeId, "ARTICLE_UPDATED", article, before, after);
    return article;
  }

  @Transactional
  public Article publish(Long storeId, String id, Publication publication, long expectedVersion) {
    var article = find(storeId, id);
    article.requireVersion(expectedVersion);
    var before = snapshot(article);
    article.publish(publication);
    articles.saveAndFlush(article);
    record(storeId, "ARTICLE_PUBLICATION_CHANGED", article, before, snapshot(article));
    return article;
  }

  @Transactional
  public void delete(Long storeId, String id, long expectedVersion) {
    var article = find(storeId, id);
    article.requireVersion(expectedVersion);
    var before = snapshot(article);
    articles.delete(article);
    articles.flush();
    record(storeId, "ARTICLE_DELETED", article, before, absent());
  }

  private void record(
      Long storeId,
      String action,
      Article article,
      Map<String, String> before,
      Map<String, String> after) {
    audit.recordCurrent(storeId, action, "ARTICLE", article.getId(), before, after);
  }

  /** 本文は監査に複製しない。変わったことは更新の redacted_fields_changed で表す。 */
  private static Map<String, String> snapshot(Article article) {
    var values = new LinkedHashMap<String, String>();
    values.put("exists", "true");
    values.put("title", article.getTitle());
    values.put("cover_image_url", Objects.toString(article.getCoverImageUrl(), ""));
    values.putAll(article.getPublication().auditValues());
    values.put("version", String.valueOf(article.getVersion()));
    return values;
  }

  private static Map<String, String> absent() {
    return Map.of("exists", "false");
  }

  private Article find(Long storeId, String id) {
    return articles
        .findByIdAndStoreId(id, storeId)
        .orElseThrow(() -> new NotFoundException("記事が見つかりません"));
  }
}
