package com.kizuna.advertising.domain;

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
@Table(name = "t_advertising_changes")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdvertisingChange extends StoreScopedEntity {

  @Column(nullable = false, updatable = false, length = 7)
  private String month;

  @Column(nullable = false, updatable = false, length = 64)
  private String costId;

  @Column(nullable = false, updatable = false)
  private Long actorId;

  @Column(nullable = false, updatable = false, length = 20)
  private String action;

  @Column(updatable = false, length = 500)
  private String reason;

  @Column(updatable = false, length = 64)
  private String sourceCostId;

  @Column(updatable = false)
  private Long versionBefore;

  @Column(updatable = false)
  private Long versionAfter;

  @Column(nullable = false, updatable = false, columnDefinition = "text")
  private String beforeValue;

  @Column(nullable = false, updatable = false, columnDefinition = "text")
  private String afterValue;

  @Builder
  public AdvertisingChange(
      String month,
      String costId,
      Long actorId,
      String action,
      String reason,
      String sourceCostId,
      Long versionBefore,
      Long versionAfter,
      String beforeValue,
      String afterValue) {
    this.month = month;
    this.costId = costId;
    this.actorId = actorId;
    this.action = action;
    this.reason = reason;
    this.sourceCostId = sourceCostId;
    this.versionBefore = versionBefore;
    this.versionAfter = versionAfter;
    this.beforeValue = beforeValue;
    this.afterValue = afterValue;
  }
}
