package com.kizuna.order.api.dto;

import com.kizuna.order.domain.ReceptionRoute;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.Data;

@Data
public class OrderCreateRequest {
  @NotNull(message = "受付は必須です")
  private Long receptionistId;

  @NotNull private LocalDate businessDate;

  private LocalTime arrivalScheduledStartTime;
  private LocalTime arrivalScheduledEndTime;

  private String customerId;

  // 上限は行き先の列と同じ（t_customers.name / t_orders.contact_name = VARCHAR(255)、
  // t_customers.phone_number / t_orders.contact_phone_number = VARCHAR(50)）。契約で撥ねないと、
  // 溢れた値が挿入時の SQLSTATE 22001 になり、理由の分かる 400 ではなく 500 で返る。
  @Size(max = 255, message = "お客様名は 255 文字以内です")
  private String customerName;

  @Size(max = 50, message = "電話番号は 50 文字以内です")
  private String phoneNumber;

  private String phoneNumber2;
  private String address;
  private String buildingName;
  private String classification;
  private String landmark;
  private Boolean hasPet;
  private String ngType;
  private String ngContent;

  @NotBlank(message = "キャストIDは必須です")
  private String castId;

  @Min(value = 1, message = "人数は 1 以上です")
  private Integer pax;

  private Integer courseMinutes;
  private Integer extensionMinutes;
  private List<String> optionCodes;
  private String discountName;
  private Integer manualDiscount;

  /** 受付経路。実際の受付手段を記録する値で、未指定は「不明」を意味する（既定値で補完しない）。 */
  private ReceptionRoute receptionRoute;

  private String carrier;
  private String mediaName;
  private String remarks;
  private String castDriverMessage;
}
