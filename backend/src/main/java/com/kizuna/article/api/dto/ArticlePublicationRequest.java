package com.kizuna.article.api.dto;

import com.kizuna.shared.publication.Publication;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record ArticlePublicationRequest(
    @NotNull(message = "公開・非公開を指定してください") Boolean published,
    OffsetDateTime publishFrom,
    OffsetDateTime publishUntil,
    Integer displayOrder,
    @NotNull(message = "編集の対象バージョンは必須です") Long version) {
  public Publication publication() {
    return new Publication(published, publishFrom, publishUntil, displayOrder);
  }
}
