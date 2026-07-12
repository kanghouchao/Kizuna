package com.kizuna.order.domain;

import com.kizuna.shared.persistence.TenantScopedEntity;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "t_orders")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Filter(name = "storeSetFilter", condition = "tenant_id in (:storeIds)")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order extends TenantScopedEntity {

  @Column(name = "store_name")
  private String storeName;

  @Column(name = "receptionist_id")
  private String receptionistId;

  @Column(name = "business_date", nullable = false)
  private LocalDate businessDate;

  @Column(name = "arrival_scheduled_start_time")
  private LocalTime arrivalScheduledStartTime;

  @Column(name = "arrival_scheduled_end_time")
  private LocalTime arrivalScheduledEndTime;

  @Column(name = "customer_id")
  private String customerId;

  @Column(name = "cast_id")
  private String castId;

  @Column(name = "course_minutes")
  private Integer courseMinutes;

  @Column(name = "extension_minutes")
  private Integer extensionMinutes;

  @Type(JsonBinaryType.class)
  @Column(name = "option_codes", columnDefinition = "jsonb")
  private List<String> optionCodes;

  @Column(name = "discount_name")
  private String discountName;

  @Column(name = "manual_discount")
  private Integer manualDiscount;

  @Column(name = "carrier")
  private String carrier;

  @Column(name = "media_name")
  private String mediaName;

  @Column(name = "used_points")
  private Integer usedPoints;

  @Column(name = "manual_grant_points")
  private Integer manualGrantPoints;

  @Column(name = "survey_status")
  private String surveyStatus;

  @Column(name = "location_address")
  private String locationAddress;

  @Column(name = "location_building")
  private String locationBuilding;

  @Column(name = "actual_arrival_time")
  private LocalTime actualArrivalTime;

  @Column(name = "actual_end_time")
  private LocalTime actualEndTime;

  @Column(name = "remarks")
  private String remarks;

  @Column(name = "cast_driver_message")
  private String castDriverMessage;

  @Enumerated(EnumType.STRING)
  @Column(name = "status")
  private OrderStatus status;

  /** キャストを割り当てる（存在確認は application 層の責務）。 */
  public void assignCast(String castId) {
    this.castId = castId;
  }

  /** 受付担当者を割り当てる（存在確認は application 層の責務）。 */
  public void assignReceptionist(String receptionistId) {
    this.receptionistId = receptionistId;
  }

  /** 顧客を紐付ける（存在確認・検索/作成は application 層の責務）。 */
  public void linkCustomer(String customerId) {
    this.customerId = customerId;
  }

  /** 部分更新コマンドを適用する。null のフィールドは変更しない。 */
  public void apply(OrderPatch patch) {
    if (patch.storeName() != null) {
      this.storeName = patch.storeName();
    }
    if (patch.arrivalScheduledStartTime() != null) {
      this.arrivalScheduledStartTime = patch.arrivalScheduledStartTime();
    }
    if (patch.arrivalScheduledEndTime() != null) {
      this.arrivalScheduledEndTime = patch.arrivalScheduledEndTime();
    }
    if (patch.courseMinutes() != null) {
      this.courseMinutes = patch.courseMinutes();
    }
    if (patch.extensionMinutes() != null) {
      this.extensionMinutes = patch.extensionMinutes();
    }
    if (patch.optionCodes() != null) {
      this.optionCodes = patch.optionCodes();
    }
    if (patch.discountName() != null) {
      this.discountName = patch.discountName();
    }
    if (patch.manualDiscount() != null) {
      this.manualDiscount = patch.manualDiscount();
    }
    if (patch.usedPoints() != null) {
      this.usedPoints = patch.usedPoints();
    }
    if (patch.manualGrantPoints() != null) {
      this.manualGrantPoints = patch.manualGrantPoints();
    }
    if (patch.remarks() != null) {
      this.remarks = patch.remarks();
    }
    if (patch.castDriverMessage() != null) {
      this.castDriverMessage = patch.castDriverMessage();
    }
  }

  /** 注文を確認済みにする。 */
  public void confirm() {
    transitionTo(OrderStatus.CONFIRMED);
  }

  /** 注文を完了する。確認済みの注文のみ完了できる。 */
  public void complete() {
    transitionTo(OrderStatus.COMPLETED);
  }

  /** 注文をキャンセルする。完了前のみ可能。 */
  public void cancel() {
    transitionTo(OrderStatus.CANCELLED);
  }

  /** 指定ステータスへ遷移する。同一ステータスへは冪等（何もしない）、不正な遷移はドメイン例外を投げる。 */
  public void transitionTo(OrderStatus target) {
    if (status == target) {
      return;
    }
    if (status == null || !status.canTransitionTo(target)) {
      throw new IllegalOrderStateTransitionException(status, target);
    }
    this.status = target;
  }

  @Override
  public String toString() {
    return "Order(id="
        + getId()
        + ", storeName="
        + storeName
        + ", businessDate="
        + businessDate
        + ", status="
        + status
        + ")";
  }
}
