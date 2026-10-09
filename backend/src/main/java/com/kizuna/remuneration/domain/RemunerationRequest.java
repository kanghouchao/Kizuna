package com.kizuna.remuneration.domain;

import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "t_remuneration_requests")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RemunerationRequest extends StoreScopedEntity {

  @Column(nullable = false, updatable = false)
  private Long actorId;

  @Column(nullable = false, updatable = false)
  private UUID requestId;

  @Column(nullable = false, updatable = false, columnDefinition = "text")
  private String requestValue;

  @Column(nullable = false, updatable = false, columnDefinition = "text")
  private String responseValue;

  @Builder
  public RemunerationRequest(
      Long actorId, UUID requestId, String requestValue, String responseValue) {
    this.actorId = actorId;
    this.requestId = requestId;
    this.requestValue = requestValue;
    this.responseValue = responseValue;
  }
}
