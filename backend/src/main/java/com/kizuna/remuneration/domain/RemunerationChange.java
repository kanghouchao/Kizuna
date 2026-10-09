package com.kizuna.remuneration.domain;

import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "t_remuneration_changes")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RemunerationChange extends StoreScopedEntity {

  @Column(nullable = false, updatable = false)
  private Long personId;

  @Column(nullable = false, updatable = false)
  private Long actorId;

  @Column(nullable = false, updatable = false, length = 32)
  private String resourceType;

  @Column(nullable = false, updatable = false, length = 64)
  private String resourceId;

  @Column(nullable = false, updatable = false, length = 32)
  private String action;

  @Column(nullable = false, updatable = false, length = 500)
  private String reason;

  @Column(nullable = false, updatable = false, columnDefinition = "text")
  private String beforeValue;

  @Column(nullable = false, updatable = false, columnDefinition = "text")
  private String afterValue;

  @Builder
  public RemunerationChange(
      Long personId,
      Long actorId,
      String type,
      String id,
      String action,
      String reason,
      String before,
      String after) {
    this.personId = personId;
    this.actorId = actorId;
    this.resourceType = type;
    this.resourceId = id;
    this.action = action;
    this.reason = reason.strip();
    this.beforeValue = before;
    this.afterValue = after;
  }
}
