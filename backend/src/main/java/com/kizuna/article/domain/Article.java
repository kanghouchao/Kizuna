package com.kizuna.article.domain;

import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.persistence.StoreScopedEntity;
import com.kizuna.shared.publication.Publication;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 店舗が公式サイトに掲出する記事。どの業務記録からも参照されないので物理削除できる。 */
@Entity
@Table(name = "t_articles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Article extends StoreScopedEntity {
  @Column(nullable = false, length = 100)
  private String title;

  @Column(nullable = false, length = 10000)
  private String body;

  @Column(length = 500)
  private String coverImageUrl;

  @Embedded private Publication publication;

  /** 書きかけを公開しないよう、作成直後は非公開で始まる。 */
  public static Article write(Long storeId, String title, String body, String coverImageUrl) {
    var article = new Article();
    article.setStoreId(storeId);
    article.title = text(title);
    article.body = text(body);
    article.coverImageUrl = cover(coverImageUrl);
    article.publication = Publication.HIDDEN;
    return article;
  }

  /** 本文が変わったかを返す。監査は本文を複製せず、変わった事実だけを残すため。 */
  public boolean edit(String title, String body, String coverImageUrl) {
    boolean bodyChanged = !Objects.equals(this.body, text(body));
    this.title = text(title);
    this.body = body;
    this.coverImageUrl = cover(coverImageUrl);
    return bodyChanged;
  }

  // 管理画面は外した表紙を空文字で送る。空のまま保存すると公式サイトに中身の無い画像が出るため、無しにそろえる。
  private static String cover(String value) {
    return value == null || value.isBlank() ? null : text(value);
  }

  // PostgreSQL の文字列は NUL を保存できず、通すと 500 になるため入力誤りとして先に弾く。
  private static String text(String value) {
    if (value.indexOf('\0') >= 0) throw new ServiceException("使用できない文字が含まれています");
    return value;
  }

  public void publish(Publication next) {
    publication = next;
  }

  /**
   * 開いた時点の版との照合。陳腐化したフォームの提出は JPA の @Version では捕まらない（再読込後の正当な更新に見える）ため、 往復させた版を明示比較して 409 で拒否する。
   */
  public void requireVersion(long expected) {
    if (getVersion() != expected) throw new ConflictException("他の担当者が記事を更新しました。最新の内容を確認してください");
  }
}
