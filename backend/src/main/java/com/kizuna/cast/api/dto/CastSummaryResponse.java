package com.kizuna.cast.api.dto;

import com.kizuna.cast.domain.CastInvitationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * キャスト一覧の 1 行。名簿として見分け、招待の進み具合を判断するのに要る項目だけを持つ。
 *
 * <p>紹介文・カスタム項目・作成更新時刻は 1 件を開いたときにだけ要るので詳細の読み口が返す。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CastSummaryResponse {
  private String id;
  private String name;
  private String status;
  private String photoUrl;
  private Integer age;
  private Integer bust;
  private Integer waist;
  private Integer hip;
  private Integer displayOrder;
  private CastInvitationStatus invitationStatus;
}
