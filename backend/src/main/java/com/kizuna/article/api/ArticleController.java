package com.kizuna.article.api;

import com.kizuna.article.api.dto.ArticleIdResponse;
import com.kizuna.article.api.dto.ArticlePublicationRequest;
import com.kizuna.article.api.dto.ArticleRequest;
import com.kizuna.article.api.dto.ArticleResponse;
import com.kizuna.article.api.dto.ArticleSummaryResponse;
import com.kizuna.article.application.ArticleService;
import com.kizuna.shared.storescope.CurrentStore;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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

/** 記事の管理 API。一覧は更新の新しい順に固定し、呼出側の並び指定は受けない。 */
@RestController
@RequestMapping("/articles")
@RequiredArgsConstructor
public class ArticleController {
  private final ArticleService articles;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('PERM_ARTICLE_MANAGE')")
  public ArticleIdResponse create(
      @CurrentStore Long storeId, @Valid @RequestBody ArticleRequest request) {
    return articles.create(storeId, request.title(), request.body());
  }

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_ARTICLE_MANAGE')")
  public Page<ArticleSummaryResponse> list(
      @CurrentStore Long storeId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return articles.list(storeId, page, size);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_ARTICLE_MANAGE')")
  public ArticleResponse get(@CurrentStore Long storeId, @PathVariable String id) {
    return articles.get(storeId, id);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_ARTICLE_MANAGE')")
  public ArticleResponse edit(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @Valid @RequestBody ArticleRequest request) {
    return articles.edit(storeId, id, request.title(), request.body(), request.expectedVersion());
  }

  @PutMapping("/{id}/publication")
  @PreAuthorize("hasAuthority('PERM_ARTICLE_MANAGE')")
  public ArticleResponse publish(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @Valid @RequestBody ArticlePublicationRequest request) {
    return articles.publish(storeId, id, request.publication(), request.version());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('PERM_ARTICLE_MANAGE')")
  public void delete(
      @CurrentStore Long storeId, @PathVariable String id, @RequestParam long version) {
    articles.delete(storeId, id, version);
  }
}
