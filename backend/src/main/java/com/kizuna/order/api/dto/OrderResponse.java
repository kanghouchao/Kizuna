package com.kizuna.order.api.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
  private String id;
  private Long receptionistId;
  private String receptionistName;
  private LocalDate businessDate;
  private LocalTime arrivalScheduledStartTime;
  private LocalTime arrivalScheduledEndTime;
  private String customerId;
  private String customerName;
  // 受付で録入された連絡先。顧客が着かなかった受注にだけ入る（着いた受注では台帳の行が名乗りを持つ）
  private String contactName;
  private String contactPhoneNumber;
  private String castId;
  private String castName;
  private Integer pax;
  private Integer courseMinutes;
  private Integer extensionMinutes;
  private List<String> optionCodes;
  private String discountName;
  private Integer manualDiscount;
  private String carrier;
  private String mediaName;
  private Integer totalFee;
  private Integer usedPoints;
  private Integer autoGrantPoints;
  private String remarks;
  private String castDriverMessage;
  private String status;
  private String receptionRoute;
  private String requesterMemberCode; // 申請した会員（店舗が起こした受注では null）

  /** 申請時に会員が店舗へ名乗った名前。当店に台帳行の無い会員の未確定申請では、これが唯一の名乗りになる。 */
  private String requesterDeclaredName;

  private String locationAddress;
  private String locationBuilding;

  // 取消の記録（理由・実行者・時刻）。取消していない受注では応答から消える。会員本人へ返す
  // MemberOrderResponse は項目の白名単なので、この理由が会員へ渡ることはない
  private String cancelledReason;

  /** 取消の実行者の表示名。操作者が削除された取消では欠落する（FK が SET NULL のため）。 */
  private String cancelledByName;

  private OffsetDateTime cancelledAt;
}
