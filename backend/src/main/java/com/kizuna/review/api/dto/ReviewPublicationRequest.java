package com.kizuna.review.api.dto;

import com.kizuna.review.domain.ReviewChange;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.publication.Publication;
import java.time.OffsetDateTime;
import tools.jackson.databind.annotation.JsonDeserialize;

public record ReviewPublicationRequest(
    @JsonDeserialize(using = ReviewVersionDeserializer.class) Long version,
    Boolean published,
    OffsetDateTime publishFrom,
    OffsetDateTime publishUntil,
    Integer displayOrder,
    String dedupeKey) {
  public ReviewChange change(String id) {
    if (published == null) throw new ServiceException("公開・非公開を指定してください");
    return ReviewChange.publication(
        id,
        version,
        new Publication(published, publishFrom, publishUntil, displayOrder),
        dedupeKey);
  }
}
