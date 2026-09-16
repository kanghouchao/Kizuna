package com.kizuna.order.domain;

import com.kizuna.shared.persistence.StoreScopedEntity;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "t_order_special_service_events")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderSpecialServiceEvent extends StoreScopedEntity {
  private String orderId;
  private String enrollmentId;
  private String serviceId;
  private String consentEventId;
  private Long actorId;
  private OffsetDateTime occurredAt;
  private String kind;

  @Type(JsonBinaryType.class)
  @Column(name = "before_snapshot", columnDefinition = "jsonb")
  private List<SpecialServiceHistorySnapshot> before;

  @Type(JsonBinaryType.class)
  @Column(name = "after_snapshot", columnDefinition = "jsonb")
  private List<SpecialServiceHistorySnapshot> after;

  private int previousTotalFee;
  private int totalFee;
  private String resolution;
  private String rejectionEventId;

  public static OrderSpecialServiceEvent rejected(
      Order order,
      List<SpecialServiceHistorySnapshot> previous,
      String enrollment,
      String service,
      String consentEvent,
      Long actor,
      OffsetDateTime at) {
    var e = new OrderSpecialServiceEvent();
    e.orderId = order.getId();
    e.enrollmentId = enrollment;
    e.serviceId = service;
    e.consentEventId = consentEvent;
    e.actorId = actor;
    e.occurredAt = at;
    e.kind = "REJECTED";
    e.before = previous;
    e.after =
        previous.stream()
            .map(
                s ->
                    new SpecialServiceHistorySnapshot(
                        s.snapshot(),
                        s.requiresAttention()
                            || (s.snapshot().serviceId().equals(service)
                                && s.snapshot().enrollmentId().equals(enrollment))))
            .toList();
    e.previousTotalFee = order.getTotalFee();
    e.totalFee = e.previousTotalFee;
    return e;
  }

  public OrderSpecialServiceEvent resolved(
      Order order,
      List<SpecialServiceHistorySnapshot> previous,
      List<SpecialServiceHistorySnapshot> current,
      int previousTotal,
      String resolution,
      Long actor) {
    var e = new OrderSpecialServiceEvent();
    e.orderId = orderId;
    e.enrollmentId = enrollmentId;
    e.serviceId = serviceId;
    e.consentEventId = consentEventId;
    e.actorId = actor;
    e.occurredAt = OffsetDateTime.now();
    e.kind = "RESOLVED";
    e.before = previous;
    e.after = current;
    e.previousTotalFee = previousTotal;
    e.totalFee = order.getTotalFee();
    e.resolution = resolution;
    e.rejectionEventId = getId();
    return e;
  }
}
