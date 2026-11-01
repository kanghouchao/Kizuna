package com.kizuna.article.api;

import com.kizuna.article.api.dto.ArticleIdResponse;
import com.kizuna.article.api.dto.ArticlePublicationRequest;
import com.kizuna.article.api.dto.ArticleRequest;
import com.kizuna.article.api.dto.ArticleResponse;
import com.kizuna.article.api.dto.ArticleSummaryResponse;
import com.kizuna.article.application.ArticleService;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.CurrentStore;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/articles")
@RequiredArgsConstructor
public class ArticleController {
  private static final int MAX_PAGE_SIZE = 100;

  private final ArticleService articles;
  private final Clock clock;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('PERM_ARTICLE_MANAGE')")
  public ArticleIdResponse create(
      @CurrentStore Long storeId, @Valid @RequestBody ArticleRequest request) {
    return new ArticleIdResponse(articles.create(storeId, request.title(), request.body()));
  }

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_ARTICLE_MANAGE')")
  public Page<ArticleSummaryResponse> list(
      @CurrentStore Long storeId, @PageableDefault(size = 20) Pageable pageable) {
    if (pageable.getPageSize() > MAX_PAGE_SIZE)
      throw new ServiceException("1 ページの件数は " + MAX_PAGE_SIZE + " 件までです");
    var now = now();
    return articles.list(storeId, pageable).map(row -> ArticleSummaryResponse.of(row, now));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_ARTICLE_MANAGE')")
  public ArticleResponse get(@CurrentStore Long storeId, @PathVariable String id) {
    return ArticleResponse.of(articles.get(storeId, id), now());
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_ARTICLE_MANAGE')")
  public ArticleResponse edit(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @Valid @RequestBody ArticleRequest request) {
    var article =
        articles.edit(storeId, id, request.title(), request.body(), request.expectedVersion());
    return ArticleResponse.of(article, now());
  }

  @PutMapping("/{id}/publication")
  @PreAuthorize("hasAuthority('PERM_ARTICLE_MANAGE')")
  public ArticleResponse publish(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @Valid @RequestBody ArticlePublicationRequest request) {
    var article = articles.publish(storeId, id, request.publication(), request.version());
    return ArticleResponse.of(article, now());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('PERM_ARTICLE_MANAGE')")
  public void delete(
      @CurrentStore Long storeId, @PathVariable String id, @RequestParam long version) {
    articles.delete(storeId, id, version);
  }

  private OffsetDateTime now() {
    return OffsetDateTime.now(clock);
  }
}
