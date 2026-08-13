package com.kizuna.order.api.dto;

/**
 * 伝票トークンの申領の結果（JSON キーは snake_case）。
 *
 * <p>返すのは記帳したポイントだけ。来店の内容（店舗・日付・担当）は来店履歴の読み口が返すものであり、 申領の応答で重ねると同じ表示項目の白名単が二箇所に分かれる。
 *
 * <p>付与予定額は発行時に確定した固定値なので、0 円完了の申領では 0 が返る（来店の可視化だけが成立する）。 プリミティブで持つのは、{@code non_null}
 * 包含設定でも「付与ゼロ」の応答からキーが消えないようにするため。
 *
 * @param grantedPoints この申領で台帳へ記帳したポイント
 */
public record MemberReceiptClaimResponse(int grantedPoints) {}
