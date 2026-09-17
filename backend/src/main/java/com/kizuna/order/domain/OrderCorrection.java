package com.kizuna.order.domain;

import com.kizuna.shared.persistence.StoreScopedEntity;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.Type;

/** 訂正の前後を同じ受注版に結び付ける不変の記録。操作者削除後も提供事実を保持する。 */
@Entity
@Table(name = "t_order_corrections")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@NoArgsConstructor
public class OrderCorrection extends StoreScopedEntity {
  @Column(nullable = false, updatable = false, length = 64)
  private String orderId;

  @Column(nullable = false, updatable = false, length = 500)
  private String reason;

  @Column(updatable = false)
  private Long correctedBy;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime correctedAt;

  @Column(nullable = false, updatable = false)
  private LocalDate businessDate;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime completedAt;

  @Column(nullable = false, updatable = false)
  private long beforeVersion;

  @Column(nullable = false, updatable = false)
  private long afterVersion;

  @Type(JsonBinaryType.class)
  @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
  private OrderCorrectionSnapshot beforeSnapshot;

  @Type(JsonBinaryType.class)
  @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
  private OrderCorrectionSnapshot afterSnapshot;

  public static OrderCorrection recorded(
      Order after,
      long beforeVersion,
      OrderCorrectionSnapshot before,
      String reason,
      Long actorId,
      OffsetDateTime at) {
    if (reason == null
        || reason.isBlank()
        || reason.length() > 500
        || actorId == null
        || at == null) throw new InvalidOrderCorrectionException("訂正の理由・実行者・日時は必須です");
    if (after.getStatus() != OrderStatus.COMPLETED
        || beforeVersion < 0
        || after.getVersion() == null
        || after.getVersion() <= beforeVersion)
      throw new InvalidOrderCorrectionException("訂正前後の受注版が正しくありません");
    var record = new OrderCorrection();
    record.setStoreId(after.getStoreId());
    record.orderId = after.getId();
    record.reason = reason;
    record.correctedBy = actorId;
    record.correctedAt = at;
    record.businessDate = after.getBusinessDate();
    record.completedAt = after.getCompletedAt();
    record.beforeVersion = beforeVersion;
    record.afterVersion = after.getVersion();
    record.beforeSnapshot = before;
    record.afterSnapshot = OrderCorrectionSnapshot.of(after);
    return record;
  }
}
