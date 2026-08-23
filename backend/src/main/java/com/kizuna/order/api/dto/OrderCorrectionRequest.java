package com.kizuna.order.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import java.util.List;
import lombok.Data;

/**
 * 完了した受注の訂正。門が直せる三組（明細行・実績時刻・コーススナップショット）の<b>全量</b>を毎回送る — 省略は「変更しない」ではなく「値なし」である。
 *
 * <p>部分更新の形を採らないのは、実終了時刻・延長分数を空へ戻す訂正が要るためで、当日実績の訂正（{@code AttendanceCorrectionRequest}）と同じ作法である。
 *
 * <p>凍結字段（予定時刻・人数・指名・受付担当・備考・キャストへの伝言）はこの型に<b>存在しない</b>。未知の項目は撥ねられる設定なので、 型に無いことがそのまま 400 になる（ADR
 * 0013 の凍結は不変）。
 */
@Data
public class OrderCorrectionRequest {

  /**
   * 訂正の理由。凍結済みの記録を動かす特権操作なので必須で、取消（ADR 0013）と同じ重さで扱う。
   *
   * <p>上限は列長（500）と揃える。契約側で撥ねないと、超過が 400 ではなく DB のエラーになる。
   */
  @NotBlank(message = "訂正の理由は必須です")
  @Size(max = 500, message = "訂正の理由は 500 文字以内で入力してください")
  private String reason;

  /** 実際の到着時刻。完了後にこれを直せる口は門だけである。 */
  private LocalTime actualArrivalTime;

  /** 実際の終了時刻。同上。 */
  private LocalTime actualEndTime;

  /** 適用されたコース名の写し。上限は {@code t_orders.course_name} = VARCHAR(255)。 */
  @Size(max = 255, message = "コース名は 255 文字以内です")
  private String courseName;

  private Integer courseMinutes;

  private Integer extensionMinutes;

  /**
   * 訂正後の内訳の全量。行に同一性は無く、送られた内容がそのまま新しい内訳になる。
   *
   * <p>ポイント利用の行は含められない（門内でも編集不可 — 誤りはポイント機構経由で直す）。既にある行はこの経路で消えない。
   */
  @NotNull(message = "訂正後の内訳は必須です")
  @Valid
  private List<@NotNull(message = "明細の要素は必須です") OrderFeeLineRequest> feeLines;
}
