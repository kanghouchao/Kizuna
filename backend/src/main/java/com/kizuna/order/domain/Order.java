package com.kizuna.order.domain;

import com.kizuna.shared.persistence.StoreScopedEntity;
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
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order extends StoreScopedEntity {

  @Column(name = "receptionist_id")
  private Long receptionistId;

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

  @Column(name = "pax")
  private Integer pax;

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

  @Enumerated(EnumType.STRING)
  @Column(name = "reception_route", length = 20)
  private ReceptionRoute receptionRoute;

  /** 申請した会員。店舗が直接起こした受注では null。 */
  @Column(name = "requester_member_id")
  private Long requesterMemberId;

  /** 申請時点の会員コード。会員行が消えて requesterMemberId が欠落した後も申請者を読めるようにする。 */
  @Column(name = "requester_member_code", length = 20)
  private String requesterMemberCode;

  /** キャストを割り当てる（存在確認は application 層の責務）。 */
  public void assignCast(String castId) {
    this.castId = castId;
  }

  /** 受付担当者を割り当てる（存在確認は application 層の責務）。 */
  public void assignReceptionist(Long receptionistId) {
    this.receptionistId = receptionistId;
  }

  /** 顧客を紐付ける（存在確認・検索/作成は application 層の責務）。 */
  public void linkCustomer(String customerId) {
    this.customerId = customerId;
  }

  /** 部分更新コマンドを適用する。null のフィールドは変更しない。 */
  public void apply(OrderPatch patch) {
    if (patch.arrivalScheduledStartTime() != null) {
      this.arrivalScheduledStartTime = patch.arrivalScheduledStartTime();
    }
    if (patch.arrivalScheduledEndTime() != null) {
      this.arrivalScheduledEndTime = patch.arrivalScheduledEndTime();
    }
    if (patch.pax() != null) {
      this.pax = patch.pax();
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

  /**
   * 未確定の申請を取り下げる。確定前（CREATED）のみ可能で、確定後は店舗との調整を要するため通常のキャンセル経路に委ねる。
   *
   * <p>会員の自己キャンセルと店舗の謝絶が共有する。
   */
  public void cancelRequest() {
    if (status != OrderStatus.CREATED) {
      throw new IllegalOrderStateTransitionException(status, OrderStatus.CANCELLED);
    }
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
    return "Order(id=" + getId() + ", businessDate=" + businessDate + ", status=" + status + ")";
  }
}
