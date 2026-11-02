package com.kizuna.receipt.domain;

import com.kizuna.shared.persistence.SnowflakeId;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "t_operation_receipts")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OperationReceipt {
  @Id @SnowflakeId private String id;

  @Column(nullable = false, updatable = false)
  private Long storeId;

  @Column(nullable = false, updatable = false)
  private Long actorId;

  @Column(nullable = false, updatable = false, length = 120)
  private String operationKey;

  @Column(nullable = false, updatable = false, length = 64)
  private String fingerprint;

  @Column(nullable = false, updatable = false, length = 80)
  private String operation;

  @Column(nullable = false, updatable = false, length = 100)
  private String targetId;

  @Type(JsonBinaryType.class)
  @Column(updatable = false, columnDefinition = "jsonb")
  private Map<String, Serializable> committedFacts;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  public static OperationReceipt record(
      Long storeId,
      Long actorId,
      String operationKey,
      String fingerprint,
      String operation,
      String targetId,
      Map<String, Serializable> committedFacts,
      OffsetDateTime now) {
    var receipt = new OperationReceipt();
    receipt.storeId = storeId;
    receipt.actorId = actorId;
    receipt.operationKey = operationKey;
    receipt.fingerprint = fingerprint;
    receipt.operation = operation;
    receipt.targetId = targetId;
    receipt.committedFacts = committedFacts;
    receipt.createdAt = now;
    return receipt;
  }
}
