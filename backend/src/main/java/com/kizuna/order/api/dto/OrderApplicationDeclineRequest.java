package com.kizuna.order.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 予約申請の謝絶。理由だけを受け取り、実行者と時刻はサーバが記録する。
 *
 * <p>理由は必須（取消 ADR 0013 の先例に倣う）。会員の申請を店舗が断る判断であり、後から経緯を辿れる根拠はここにしか残らない。 <b>分類軸ではない</b>ため enum
 * 化しない。上限は列長（500）と揃える。
 */
@Data
public class OrderApplicationDeclineRequest {

  @NotBlank(message = "謝絶の理由は必須です")
  @Size(max = 500, message = "謝絶の理由は 500 文字以内で入力してください")
  private String reason;
}
