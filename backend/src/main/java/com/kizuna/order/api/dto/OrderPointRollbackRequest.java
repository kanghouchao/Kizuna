package com.kizuna.order.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 受注 1 件を根拠とするポイントの授受を打ち消す要求（巻き戻し）。理由だけを受け取り、実行者と時刻はサーバが記録する。
 *
 * <p>冪等キーは取らない。操作記録が受注 1 件につき高々 1 行であることが収束を担うので、クライアント生成キーで 再送を識別する必要が無い（ADR 0007
 * が要求するのは自然キーを持たない自由書き込みの側である）。
 *
 * <p>上限は列長（500）と揃える。契約側で撥ねないと、超過が 400 ではなく DB のエラーになる。
 */
@Data
public class OrderPointRollbackRequest {

  @NotBlank(message = "巻き戻しの理由は必須です")
  @Size(max = 500, message = "巻き戻しの理由は 500 文字以内で入力してください")
  private String reason;
}
