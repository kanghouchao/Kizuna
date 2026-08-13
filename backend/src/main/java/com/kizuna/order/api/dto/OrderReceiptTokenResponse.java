package com.kizuna.order.api.dto;

/**
 * 伝票トークンの再発行の結果（JSON キーは snake_case）。
 *
 * <p>返すのは生値だけ。受注そのものは再発行で変わらないため、完了の応答のように受注の表現へ載せない。
 *
 * <p>保存されるのはダイジェストだけなので、この応答を取り逃すと生値は二度と手に入らない。診断出力へ滲ませないよう、 この型を持ち回す側もログへ落とさないこと。
 *
 * @param receiptToken 発行された伝票トークンの生値
 */
public record OrderReceiptTokenResponse(String receiptToken) {}
