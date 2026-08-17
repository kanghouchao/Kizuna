package com.kizuna.customer.api.dto;

import java.util.List;

/**
 * 同じ第一電話番号を持つ生きた顧客のグループ。必ず 2 行以上を含む。
 *
 * <p>一致は手がかりであって判定ではない。同伴者が連絡先を共有する場合のように、同じ番号の別人は正規に起こりうる（ADR 0010）。
 *
 * <p>{@code total} はその番号を持つ生きた行の総数で、{@code customers} は上限で切られうる。切った件数を偽らないために両方を持つ。
 */
public record CustomerDuplicateGroupResponse(
    String phoneNumber, long total, List<CustomerMergeComparisonResponse> customers) {}
