package com.kizuna.article.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.publication.Publication;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ArticleTest {
  @Test
  void writtenArticleStartsHiddenInItsStore() {
    var article = Article.write(7L, "表題", "本文", null);

    assertThat(article.getStoreId()).isEqualTo(7L);
    assertThat(article.getTitle()).isEqualTo("表題");
    assertThat(article.getBody()).isEqualTo("本文");
    assertThat(article.getPublication()).isEqualTo(Publication.HIDDEN);
  }

  @Test
  void editReportsWhetherTheBodyChanged() {
    var article = Article.write(7L, "表題", "本文", null);

    assertThat(article.edit("改題", "本文", null)).isFalse();
    assertThat(article.getTitle()).isEqualTo("改題");
    assertThat(article.edit("改題", "新しい本文", null)).isTrue();
    assertThat(article.getBody()).isEqualTo("新しい本文");
  }

  @Test
  void coverIsKeptReplacedAndBlankMeansNone() {
    var article = Article.write(7L, "表題", "本文", "/static/uploads/a.png");
    assertThat(article.getCoverImageUrl()).isEqualTo("/static/uploads/a.png");

    article.edit("表題", "本文", "/static/uploads/b.png");
    assertThat(article.getCoverImageUrl()).isEqualTo("/static/uploads/b.png");
    article.edit("表題", "本文", " ");
    assertThat(article.getCoverImageUrl()).isNull();
    assertThat(Article.write(7L, "表題", "本文", "").getCoverImageUrl()).isNull();
    assertThatThrownBy(() -> article.edit("表題", "本文", "/a\0.png"))
        .isInstanceOf(ServiceException.class);
  }

  @Test
  void nulCharacterIsRejectedOnWriteAndEdit() {
    assertThatThrownBy(() -> Article.write(7L, "表\0題", "本文", null))
        .isInstanceOf(ServiceException.class);
    var article = Article.write(7L, "表題", "本文", null);
    assertThatThrownBy(() -> article.edit("表題", "本\0文", null)).isInstanceOf(ServiceException.class);
    assertThat(article.getBody()).isEqualTo("本文");
  }

  @Test
  void publishReplacesThePublication() {
    var article = Article.write(7L, "表題", "本文", null);
    var next = new Publication(true, OffsetDateTime.parse("2026-06-01T00:00:00Z"), null, 1);

    article.publish(next);

    assertThat(article.getPublication()).isEqualTo(next);
  }

  @Test
  void staleVersionIsConflict() {
    var article = Article.write(7L, "表題", "本文", null);
    ReflectionTestUtils.setField(article, "version", 2L);

    article.requireVersion(2);
    assertThatThrownBy(() -> article.requireVersion(1)).isInstanceOf(ConflictException.class);
  }
}
