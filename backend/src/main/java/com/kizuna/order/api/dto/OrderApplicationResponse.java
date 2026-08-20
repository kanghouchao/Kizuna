package com.kizuna.order.api.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 店舗の予約受付箱の 1 行。申請原文（希望内容）と処理に要る項目だけを持つ。
 *
 * <p>確定済みの行は {@code orderId} で生成された受注と対照できる。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderApplicationResponse {
  private String id;
  private LocalDate businessDate;
  private LocalTime arrivalScheduledStartTime;
  private Integer pax;
  private String castId;
  private String castName;
  private String remarks;
  private String status;
  private String requesterMemberCode;

  /** 申請時に会員が店舗へ名乗った名前。確定まで台帳行は無いので、これが申請の唯一の名乗りになる。 */
  private String requesterDeclaredName;

  /** ゲスト申請の連絡先。折返し連絡の宛先であり、確定時の新規顧客フォームの予填値になる（会員申請では null）。 */
  private String contactName;

  private String contactPhoneNumber;

  /** 確定時に生成した受注の id。確定していない申請では null。 */
  private String orderId;

  private String declinedReason;

  /** 希望日を過ぎても処理されていない申請（失効）。確定・謝絶はサーバ側でも拒否される。 */
  private boolean expired;
}
