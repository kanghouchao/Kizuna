package com.kizuna.customer.api.dto;

import java.util.List;

/**
 * 重複候補の一覧。グループ数に上限があるため、切り落としたかどうかを同じ応答で告げる。
 *
 * <p>裸の配列で返さないのはこの {@code truncated} のためである。黙って切ると、上限まで見た人が「もう重複は無い」と読んでしまう。 上限に達していても集合は有界である —
 * 統合が済むたびに片方が墓標になって候補から落ちるので、進めれば残りが現れる。
 */
public record CustomerDuplicateCandidatesResponse(
    List<CustomerDuplicateGroupResponse> groups, boolean truncated) {}
