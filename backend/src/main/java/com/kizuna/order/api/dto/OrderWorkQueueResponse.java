package com.kizuna.order.api.dto;

import com.kizuna.order.domain.OrderCourse;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 作業キューの未完了受注と通常更新の応答。会計・ポイント・取消の終端記録は含めない。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderWorkQueueResponse {
  private boolean requiresAttention;
  private int unresolvedSpecialServiceCount;
  private OffsetDateTime startedAt;
  private OrderCourse course;
  private String id;
  private Long receptionistId;
  private String receptionistName;
  private LocalDate businessDate;
  private LocalTime arrivalScheduledStartTime;
  private String castId;
  private String castName;
  private Integer pax;
  private String remarks;
  private String status;
  private String receptionRoute;
  private String requesterMemberCode;
  private String customerName;

  // 受付で録入された連絡先。顧客が着かなかった受注にだけ入る（着いた受注では台帳の行が名乗りを持つ）
  private String contactName;
  private String contactPhoneNumber;

  /** 申請時に会員が店舗へ名乗った名前。当店に台帳行の無い会員の未確定申請では、これが唯一の名乗りになる。 */
  private String requesterDeclaredName;
}
