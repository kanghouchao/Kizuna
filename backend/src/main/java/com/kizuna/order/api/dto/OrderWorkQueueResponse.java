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
  private boolean completionInvalidated;
  private String replacementForOrderId;
  private boolean requiresAttention;
  private int unresolvedSpecialServiceCount;
  private OffsetDateTime startedAt;
  private OffsetDateTime completedAt;
  private int accruedRemuneration;
  private int totalRemuneration;
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

  // 受注に記録した氏名・電話の写し。台帳の現在値とは独立する
  private String contactName;
  private String contactPhoneNumber;

  /** 申請時に会員が店舗へ名乗った名前。当店に台帳行の無い会員の未確定申請では、これが唯一の名乗りになる。 */
  private String requesterDeclaredName;
}
