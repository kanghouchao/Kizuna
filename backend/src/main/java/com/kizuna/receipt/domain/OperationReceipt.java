package com.kizuna.receipt.domain;

import com.kizuna.shared.persistence.StoreScopedEntity;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
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
public class OperationReceipt extends StoreScopedEntity {
  @Column(name = "actor_id", nullable = false, updatable = false)
  private Long actorId;

  @Column(name = "operation_key", nullable = false, updatable = false, length = 120)
  private String operationKey;

  @Column(name = "fingerprint", nullable = false, updatable = false, length = 64)
  private String fingerprint;

  @Column(name = "operation", nullable = false, updatable = false, length = 80)
  private String operation;

  @Column(name = "target_id", nullable = false, updatable = false, length = 100)
  private String targetId;

  @Type(JsonBinaryType.class)
  @Column(name = "committed_facts", updatable = false, columnDefinition = "jsonb")
  private Map<String, Serializable> committedFacts;

  public static OperationReceipt record(
      Long storeId,
      Long actorId,
      String operationKey,
      String fingerprint,
      String operation,
      String targetId,
      Map<String, Serializable> committedFacts) {
    var receipt = new OperationReceipt();
    receipt.setStoreId(storeId);
    receipt.actorId = actorId;
    receipt.operationKey = operationKey;
    receipt.fingerprint = fingerprint;
    receipt.operation = operation;
    receipt.targetId = targetId;
    receipt.committedFacts = committedFacts;
    return receipt;
  }
}
