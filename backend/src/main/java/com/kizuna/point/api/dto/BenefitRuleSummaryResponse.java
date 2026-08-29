package com.kizuna.point.api.dto;

import java.time.LocalDate;

/**
 * 特典規則一覧 1 件の要約応答。適用店舗は件数だけで、店舗 ID の列挙は詳細（GET /platform/benefit-rules/{id}）が持つ。
 *
 * @param storeCount 発火を拾う店舗の件数。全店舗の規則では 0
 * @param enabled 停用されていない規則が true。停用済みも一覧には並ぶ（削除しないため）
 * @param version 楽観ロック用バージョン。停用の入口が一覧の行なので、要約も運ぶ
 */
public record BenefitRuleSummaryResponse(
    Long id,
    String name,
    String type,
    String storeScopeType,
    int storeCount,
    LocalDate effectiveFrom,
    LocalDate effectiveUntil,
    Integer grantValidityDays,
    String repeatPolicy,
    Integer points,
    Integer referrerPoints,
    Integer referredPoints,
    boolean enabled,
    Long version) {}
