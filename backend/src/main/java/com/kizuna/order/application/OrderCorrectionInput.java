package com.kizuna.order.application;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalTime;
import java.util.List;

/** 完了受注の訂正内容。通常明細は全量、実績時刻の省略は値なし。コース・特殊サービスの版指定の省略は既存の約定を保持する。試算と訂正の双方がこの型を署名の入力にする。 */
public record OrderCorrectionInput(
    List<String> specialServiceRevisionIds,
    @JsonIgnore String confirmationToken,
    String courseRevisionId,
    Long expectedVersion,
    String reason,
    LocalTime actualArrivalTime,
    LocalTime actualEndTime,
    List<OrderFeeLineInput> feeLines) {}
