package com.kizuna.article.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.article.domain.Article;
import com.kizuna.article.domain.ArticleRepository;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.publication.Publication;
import com.kizuna.user.application.BusinessAudit;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ArticleServiceTest {
  private static final Long STORE = 7L;
  private final ArticleRepository articles = mock(ArticleRepository.class);
  private final BusinessAudit audit = mock(BusinessAudit.class);
  private final ArticleService service = new ArticleService(articles, audit);
  private Article article;

  @BeforeEach
  void stored() {
    article = Article.write(STORE, "表題", "秘密の本文");
    ReflectionTestUtils.setField(article, "id", "a1");
    ReflectionTestUtils.setField(article, "version", 0L);
    when(articles.findByIdAndStoreId("a1", STORE)).thenReturn(Optional.of(article));
    when(articles.saveAndFlush(any(Article.class)))
        .thenAnswer(
            call -> {
              Article saved = call.getArgument(0);
              if (saved.getId() == null) ReflectionTestUtils.setField(saved, "id", "new");
              if (saved.getVersion() == null) ReflectionTestUtils.setField(saved, "version", 0L);
              return saved;
            });
  }

  @Test
  void createAuditsWithoutTheBody() {
    var created = service.create(STORE, "新しい記事", "秘密の本文");

    assertThat(created).isEqualTo("new");
    var after = captureAfter("ARTICLE_CREATED", "new");
    assertThat(after).containsEntry("title", "新しい記事").containsEntry("published", "false");
    assertThat(after.values()).noneMatch(v -> v.contains("秘密"));
  }

  @Test
  void editMarksTheBodyAsChangedWithoutCopyingIt() {
    var edited = service.edit(STORE, "a1", "改題", "差し替えた秘密", 0);

    assertThat(edited.getBody()).isEqualTo("差し替えた秘密");
    var after = captureAfter("ARTICLE_UPDATED", "a1");
    assertThat(after).containsEntry("redacted_fields_changed", "body");
    assertThat(after.values()).noneMatch(v -> v.contains("秘密"));
  }

  @Test
  void editKeepingTheBodyDoesNotMarkIt() {
    service.edit(STORE, "a1", "改題", "秘密の本文", 0);

    assertThat(captureAfter("ARTICLE_UPDATED", "a1")).doesNotContainKey("redacted_fields_changed");
  }

  @Test
  void publishReplacesThePublicationAndAuditsIt() {
    var next = new Publication(true, OffsetDateTime.parse("2026-06-01T00:00:00Z"), null, 3);

    var published = service.publish(STORE, "a1", next, 0);

    assertThat(published.getPublication()).isEqualTo(next);
    assertThat(captureAfter("ARTICLE_PUBLICATION_CHANGED", "a1"))
        .containsEntry("published", "true")
        .containsEntry("display_order", "3");
  }

  @Test
  void deleteRemovesAndAuditsAbsence() {
    service.delete(STORE, "a1", 0);

    verify(articles).delete(article);
    assertThat(captureAfter("ARTICLE_DELETED", "a1")).containsExactly(Map.entry("exists", "false"));
  }

  @Test
  void staleVersionIsConflictAndNothingIsWritten() {
    assertThatThrownBy(() -> service.edit(STORE, "a1", "t", "b", 1))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> service.publish(STORE, "a1", Publication.HIDDEN, 1))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> service.delete(STORE, "a1", 1)).isInstanceOf(ConflictException.class);
    verify(articles, never()).delete(any());
    verify(audit, never())
        .recordCurrent(any(), anyString(), anyString(), anyString(), anyMap(), anyMap());
  }

  @Test
  void anotherStoresArticleIsNotFound() {
    when(articles.findByIdAndStoreId("a1", 8L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.get(8L, "a1")).isInstanceOf(NotFoundException.class);
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> captureAfter(String action, String id) {
    ArgumentCaptor<Map<String, String>> after = ArgumentCaptor.forClass(Map.class);
    verify(audit)
        .recordCurrent(eq(STORE), eq(action), eq("ARTICLE"), eq(id), anyMap(), after.capture());
    return after.getValue();
  }
}
