package com.kizuna.order.api.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会員本人に返す予約の表現。
 *
 * <p>店舗の顧客台帳（ランク・区分・NG・ポイント・連絡先など）は店舗の内部情報であり、本人であっても会員側の経路からは到達できてはならない。 そのため本人が申請した内容と状態だけを持つ。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberOrderResponse {
  private String id;
  private Long storeId;
  private String storeName;
  private LocalDate businessDate;
  private LocalTime arrivalScheduledStartTime;
  private Integer pax;
  private String castName;
  private String status;
}
