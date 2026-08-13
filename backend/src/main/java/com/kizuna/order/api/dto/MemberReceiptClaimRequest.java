package com.kizuna.order.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 伝票トークンの申領。所持しているトークンの生値だけを受け取る — 受注 ID は受け取らない。 受注 ID は時刻順に列挙でき秘密ではないため、所持の証明にならない（ADR 0008）。
 *
 * <p>生値は要求の本体で受ける。パスや問い合わせ文字列に載せると、リバースプロキシとアプリのアクセスログに 90 日有効の クレデンシャルが残る。
 */
@Data
public class MemberReceiptClaimRequest {

  @NotBlank(message = "伝票トークンは必須です")
  private String token;
}
