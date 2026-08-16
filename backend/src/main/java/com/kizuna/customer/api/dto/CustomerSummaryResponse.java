package com.kizuna.customer.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 顧客一覧の 1 行。目当ての顧客を絞り込んで選ぶのに要る項目だけを持つ。
 *
 * <p>住所・NG の内容・利用エリアといった応対中に読む項目は詳細の読み口が返す。会員コードも持たない — 一覧で要るのは紐づけの有無だけである。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerSummaryResponse {
  private String id;
  private String name;
  private String phoneNumber;
  private String lineId;
  private String rank;
  private String classification;

  /** 会員紐づけの有無。関連状態の投影であり、応答では必ず真偽値が入る（null にはならない）。 */
  private Boolean memberLinked;

  private String ngType;
}
