package com.kizuna.order.domain;

import java.time.LocalTime;
import java.util.List;

/**
 * 完了後訂正の入力。門が直せる三組（実績時刻・コーススナップショット・明細行）の<b>全量</b>を運ぶ — null は「変更しない」ではなく「値なし」である。
 *
 * <p>部分更新の形を採らないのは、実終了時刻・延長分数を空へ戻す訂正が要るためで、当日実績の訂正（{@code AttendanceCorrectionRequest}）と同じ作法である。
 *
 * <p>凍結字段（予定時刻・人数・指名・受付担当・備考・伝言）はこの型に存在しない。門の射程を型の側から固定し、要求に載せられても届かないようにする。
 */
public record OrderCorrectionCommand(
    LocalTime actualArrivalTime,
    LocalTime actualEndTime,
    String courseName,
    Integer courseMinutes,
    Integer extensionMinutes,
    List<OrderFeeLineDraft> feeLines) {}
