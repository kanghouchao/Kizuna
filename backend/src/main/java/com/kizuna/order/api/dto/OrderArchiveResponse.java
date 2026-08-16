package com.kizuna.order.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * アーカイブ（完了・取消）の 1 行。終端状態の受注を振り返るための会計・ポイントと、取消の記録を持つ。
 *
 * <p>指名・受付担当・備考は対応中にしか使わないので載せない。1 件の全項目は詳細の読み口が返す。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderArchiveResponse {
  private String id;
  private String status;
  private LocalDate businessDate;
  private Integer totalFee;
  private Integer autoGrantPoints;
  private Integer usedPoints;

  // 取消の記録（時刻・実行者・理由）。取消していない受注では応答から消える
  private OffsetDateTime cancelledAt;

  /** 取消の実行者の表示名。操作者が削除された取消では欠落する（FK が SET NULL のため）。 */
  private String cancelledByName;

  private String cancelledReason;
  private String customerName;

  // 受付で録入された連絡先。顧客が着かなかった受注にだけ入る
  private String contactName;
  private String contactPhoneNumber;

  private String requesterDeclaredName;
}
