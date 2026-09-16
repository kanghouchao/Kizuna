package com.kizuna.order.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import java.util.List;
import lombok.Data;

/** 完了受注の訂正。通常明細は全量必須、実績時刻の省略は値なし。 コース・特殊サービスの歴史版本指定の省略は既存約定を保持する。予定・人数・担当・受付・備考・伝言は変更できない。 */
@Data
public class OrderCorrectionRequest {
  private List<String> specialServiceRevisionIds;
  private String confirmationToken;
  private String courseRevisionId;

  /** 表示後の変更を古い内容で上書きしないため、画面が読んだ版と現物の版を照合する。 */
  @NotNull(message = "訂正の対象バージョンは必須です")
  private Long expectedVersion;

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

  /**
   * 訂正後の内訳の全量。未変更行はIDで維持し、追加・置換する行だけを採用する。
   *
   * <p>ポイント利用の行は含められない（門内でも編集不可 — 誤りはポイント機構経由で直す）。既にある行はこの経路で消えない。
   */
  @NotNull(message = "訂正後の内訳は必須です")
  @Valid
  private List<@NotNull(message = "明細の要素は必須です") OrderFeeLineRequest> feeLines;
}
