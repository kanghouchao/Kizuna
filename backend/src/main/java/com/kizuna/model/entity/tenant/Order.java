package com.kizuna.model.entity.tenant;

import com.kizuna.model.entity.tenant.security.TenantUser;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "t_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order extends BaseEntity {

  @Column(name = "store_name")
  private String storeName;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "receptionist_id")
  private TenantUser receptionist;

  @Column(name = "business_date", nullable = false)
  private LocalDate businessDate;

  @Column(name = "arrival_scheduled_start_time")
  private LocalTime arrivalScheduledStartTime;

  @Column(name = "arrival_scheduled_end_time")
  private LocalTime arrivalScheduledEndTime;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "customer_id")
  private Customer customer;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "cast_id")
  private Cast cast;

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

  @Column(name = "status")
  private String status;

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
