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
  @Column(nullable = false, updatable = false, length = 30)
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

  @Column(nullable = false, updatable = false, length = 64)
  private String operationId;

  @Enumerated(EnumType.STRING)
  @Column(updatable = false, length = 10)
  private ContactPurpose purpose;

  @Column(length = 200, updatable = false)
  private String source;

  @Column(length = 2000, updatable = false)
  private String reason;

  @Column(updatable = false, length = 64)
  private String sourceContactId;

  @Column(updatable = false, length = 64)
  private String applicationId;

  public static CustomerContactHistory guestConsent(
      CustomerContact contact,
      Long actorId,
      ContactState before,
      String applicationId,
      String evidence,
      String operationId) {
    var history =
        permission(
            contact,
            actorId,
            before,
            ContactPurpose.MARKETING,
            "ゲスト申請 " + applicationId,
            evidence,
            operationId);
    history.applicationId = applicationId;
    return history;
  }

  public static CustomerContactHistory permission(
      CustomerContact contact,
      Long actorId,
      ContactState before,
      ContactPurpose purpose,
      String source,
      String reason,
      String operationId) {
    var history = record(contact, ContactAction.PERMISSION_CHANGE, actorId, before, operationId);
    history.purpose = purpose;
    history.source = source;
    history.reason = reason;
    return history;
  }

  public static CustomerContactHistory inheritance(
      CustomerContact contact,
      Long actorId,
      ContactState before,
      String operationId,
      String sourceContactId) {
    var history =
        record(contact, ContactAction.RESTRICTION_INHERITANCE, actorId, before, operationId);
    history.sourceContactId = sourceContactId;
    return history;
  }

  public static CustomerContactHistory record(
      CustomerContact contact,
      ContactAction action,
      Long actorId,
      ContactState before,
      String operationId) {
    var history = new CustomerContactHistory();
    history.operationId = operationId;
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
