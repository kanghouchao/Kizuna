package com.kizuna.customer.domain;

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
@Table(name = "t_customer_contact_history")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@NoArgsConstructor
public class CustomerContactHistory extends StoreScopedEntity {
  @Column(nullable = false, updatable = false)
  private String contactId;

  @Column(nullable = false, updatable = false)
  private String originCustomerId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false)
  private ContactAction action;

  @Column(nullable = false, updatable = false)
  private Long actorId;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime occurredAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "before_state", columnDefinition = "jsonb", updatable = false)
  private ContactState before;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "after_state", columnDefinition = "jsonb", nullable = false, updatable = false)
  private ContactState after;

  public static CustomerContactHistory record(
      CustomerContact contact, ContactAction action, Long actorId, ContactState before) {
    var history = new CustomerContactHistory();
    history.contactId = contact.getId();
    history.originCustomerId = contact.getOriginCustomerId();
    history.action = action;
    history.actorId = actorId;
    history.occurredAt = OffsetDateTime.now();
    history.before = before;
    history.after = contact.state();
    return history;
  }
}
