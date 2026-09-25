package com.kizuna.order.domain;

import com.kizuna.customer.domain.ContactType;
import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "t_order_business_contact_history")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@NoArgsConstructor
public class BusinessContactHistory extends StoreScopedEntity {
  @Column(nullable = false, updatable = false)
  private String orderId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false)
  private ContactType type;

  @Column(nullable = false, updatable = false)
  private String action;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "before_state", columnDefinition = "jsonb", updatable = false)
  private BusinessContactState before;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "after_state", columnDefinition = "jsonb", updatable = false)
  private BusinessContactState after;

  @Column(updatable = false)
  private Long recordedBy;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime recordedAt;

  public static BusinessContactHistory record(
      String orderId,
      ContactType type,
      String action,
      BusinessContactState before,
      BusinessContactState after,
      Long actor) {
    var row = new BusinessContactHistory();
    row.orderId = orderId;
    row.type = type;
    row.action = action;
    row.before = before;
    row.after = after;
    row.recordedBy = actor;
    row.recordedAt = OffsetDateTime.now();
    return row;
  }
}
