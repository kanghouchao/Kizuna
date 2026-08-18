package com.kizuna.shift.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 当日実績の取消。理由だけを受け取り、実行者と時刻はサーバが記録する。
 *
 * <p>理由は必須。取消は法定保存対象の記録を導出・照会から外す不可逆な操作で、経緯を辿れる根拠はここにしか残らない（ADR 0013 の作法に揃える）。
 *
 * <p>これは<b>分類軸ではない</b>。「誤記録 / 重複 / 出勤取りやめ」のような集計可能な分類が要るようになったら列を足すのであって、情報の足りない今それを enum で固めない。
 *
 * <p>上限は列長（500）と揃える。契約側で撥ねないと、超過が 400 ではなく DB のエラーになる。
 */
@Data
public class AttendanceCancellationRequest {

  @NotBlank(message = "取消の理由は必須です")
  @Size(max = 500, message = "取消の理由は 500 文字以内で入力してください")
  private String reason;
}
