package com.kizuna.customer.api.dto;

import java.util.List;

/**
 * 同じ第一電話番号を持つ生きた顧客のグループ。
 *
 * <p>一致は手がかりであって判定ではない。同伴者が連絡先を共有する場合のように、同じ番号の別人は正規に起こりうる（ADR 0010）。
 *
 * <p>{@code total} はその番号を持つ生きた行の総数。{@code customers} は全行か空のいずれかで、桁外れに大きいグループでは空になる（{@code
 * CustomerService.MAX_LISTED_GROUP_SIZE}）。件数の表示には必ず {@code total} を使う。
 */
public record CustomerDuplicateGroupResponse(
    String phoneNumber, long total, List<CustomerMergeComparisonResponse> customers) {}
