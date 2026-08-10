package com.kizuna.customer.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Data;

/**
 * 会員ポイントの手動調整の内容。
 *
 * <p>理由は省略できない。準金銭的な残高を人手で動かす操作であり、後から誰が何のために動かしたか辿れない仕訳を台帳へ積ませない。
 *
 * <p>有効期限は加算のときだけ意味を持つ（減算に指定すると台帳が撥ねる）。増減 0 の拒否も台帳側の判定で、入口ごとに規則が分かれないようにする。
 */
@Data
public class CustomerPointAdjustmentRequest {

  @NotNull(message = "増減ポイントは必須です")
  private Integer delta;

  @NotBlank(message = "調整の理由は必須です")
  @Size(max = 500, message = "調整の理由は500文字以内です")
  private String reason;

  /** 加算するポイントの有効期限。無期限なら省略する。 */
  private LocalDate expiresOn;

  /** クライアント生成の冪等キー。応答喪失後の再送を初回と同じ操作として識別する（ADR 0007）。 サーバは形式を解釈しない不透明な文字列として扱う。 */
  @NotBlank(message = "冪等キーは必須です")
  @Size(max = 64, message = "冪等キーは64文字以内です")
  private String idempotencyKey;
}
