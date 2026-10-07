package com.kizuna.audit.domain;

import com.kizuna.audit.recording.AuditChange;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "t_audit_events")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime occurredAt;

  @Column(nullable = false, updatable = false)
  private Long actorId;

  @Column(nullable = false, updatable = false, length = 20)
  private String actorType;

  @Column(nullable = false, updatable = false)
  private String actorName;

  private Long storeId;

  @Column(nullable = false, updatable = false, length = 80)
  private String action;

  @Column(nullable = false, updatable = false, length = 20)
  private String result;

  @Column(nullable = false, updatable = false, length = 80)
  private String targetType;

  @Column(nullable = false, updatable = false, length = 100)
  private String targetId;

  @Column(length = 80)
  private String sourceType;

  @Column(length = 100)
  private String sourceId;

  @Type(JsonBinaryType.class)
  @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
  private Map<String, String> beforeValues;

  @Type(JsonBinaryType.class)
  @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
  private Map<String, String> afterValues;

  public static AuditEvent record(AuditChange change, OffsetDateTime now) {
    var event = new AuditEvent();
    event.occurredAt = now;
    event.actorId = change.actor().id();
    event.actorType = change.actor().type();
    event.actorName = change.actor().name();
    event.storeId = change.storeId();
    event.action = change.action();
    event.result = change.result();
    event.targetType = change.targetType();
    event.targetId = change.targetId();
    event.sourceType = change.sourceType();
    event.sourceId = change.sourceId();
    event.beforeValues = change.beforeValues();
    event.afterValues = change.afterValues();
    return event;
  }
}
