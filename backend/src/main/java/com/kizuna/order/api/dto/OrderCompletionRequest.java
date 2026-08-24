package com.kizuna.order.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/**
 * 受注完了（会計）の内容。ポイントの付与・利用はこの経路でのみ台帳へ入る。
 *
 * <p>会計金額は受け取らない — 合計は明細の総和として導出される。会計の場で確定した内訳をここで送り、集約が差し替えてから完了する。
 * 内訳は省略できない（未指定を「内訳なし」として黙って通すと、合計 0 の完了が事故として成立する）。
 *
 * <p>ポイント利用は任意だが、指定するなら 1 以上でなければならない。利用しない完了では項目ごと省略する — 0 を送ると撥ねられる。
 */
@Data
public class OrderCompletionRequest {

  /**
   * 画面が読み込んだ時点の受注のバージョン（詳細の読み口が返す {@code version}）。
   *
   * <p>完了は終端化なので、書いた後の救済は訂正の門しか無い。要求が載せた版と現物の版を突き合わせ、ずれていれば書かずに 409 で差し戻す（終端前の通常編集は後書き勝ちのまま —
   * 上書きは再編集で直せる）。
   */
  @NotNull(message = "完了の対象バージョンは必須です")
  private Long expectedVersion;

  /** 適用されたコース名の写し。会計の場が最後の更新機会になるため、完了と同じ要求で直せる。 */
  @Size(max = 255, message = "コース名は 255 文字以内です")
  private String courseName;

  @NotNull(message = "会計の内訳は必須です")
  @Valid
  private List<@NotNull(message = "明細の要素は必須です") OrderFeeLineRequest> feeLines;

  @Min(value = 1, message = "利用ポイントは 1 以上です")
  private Integer usePoints;
}
