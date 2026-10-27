package com.kizuna.order.application;

import com.kizuna.order.domain.SpecialServiceSnapshot;

/** 受注に載った特殊サービス 1 件の現況。同意の現況は試算の表示にだけ使い、確定の照合では引かない（null）。 */
public record SpecialServiceStatus(
    SpecialServiceSnapshot snapshot, boolean requiresAttention, String currentConsentStatus) {}
