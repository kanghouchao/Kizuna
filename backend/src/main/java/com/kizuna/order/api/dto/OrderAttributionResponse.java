package com.kizuna.order.api.dto;

import java.time.OffsetDateTime;

/**
 * 店舗が見る受注 1 件の帰属の現況（JSON キーは snake_case）。
 *
 * <p>返すのは直近の帰属記録 1 件だけで、区間の履歴一覧にはしない。帰属記録は「誰の来店か」の現況を判じるためのもので、 訂正が稀な人手操作である以上、店舗が読むのは常に最新の 1 件になる。
 *
 * <p>会員側の情報は<b>会員コードだけ</b>を返す。表示名・メールはプラットフォーム側プロフィールであり店舗へ渡らない（ADR 0008 の一本道と同じ紀律）。
 *
 * <p>{@code attributed} を独立した真偽値で持つのは、{@code non_null} 包含設定では記録の無い受注から他の項目がキーごと消えるため。
 * 「帰属していない」と「読めなかった」を画面が取り違えないようにする。
 *
 * @param attributed 現にこの受注が会員へ帰属しているか（有効な帰属記録があるか）
 * @param memberCode 直近の帰属記録の会員コード。記録が 1 件も無ければ欠落する
 * @param source 成立の機構（COMPLETION / RECEIPT_TOKEN）。記録が無ければ欠落する
 * @param attributedAt 帰属が確定した日時。記録が無ければ欠落する
 * @param invalidatedReason 直近の記録が無効化済みならその理由。有効な記録・記録なしでは欠落する
 * @param invalidatedAt 無効化した日時。同上
 */
public record OrderAttributionResponse(
    boolean attributed,
    String memberCode,
    String source,
    OffsetDateTime attributedAt,
    String invalidatedReason,
    OffsetDateTime invalidatedAt) {}
