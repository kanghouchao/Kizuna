package com.kizuna.order.api.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 作業キューの 1 行。対応の要否を判断し、その場で確定・謝絶するのに要る項目だけを持つ。
 *
 * <p>会計・ポイント・取消の記録は終端状態にしか値が入らないので載せない。行を書き戻す操作（確定・更新）の応答も この型で返し、応答の形と行の形を一致させる。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderWorkQueueResponse {
  private String id;
  private Long receptionistId;
  private String receptionistName;
  private LocalDate businessDate;
  private LocalTime arrivalScheduledStartTime;
  private String castId;
  private String castName;
  private Integer pax;
  private Integer courseMinutes;
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
