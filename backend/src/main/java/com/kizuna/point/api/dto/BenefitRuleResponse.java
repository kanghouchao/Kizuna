package com.kizuna.point.api.dto;

import java.time.LocalDate;
import java.util.Set;

/**
 * 特典規則の詳細応答。編集フォームが要る店舗 ID の列挙と楽観ロック用 version を持つ。
 *
 * <p>取消方法は種別から導くので項目を持たない。
 */
public record BenefitRuleResponse(
    Long id,
    String name,
    String type,
    String storeScopeType,
    Set<Long> storeIds,
    LocalDate effectiveFrom,
    LocalDate effectiveUntil,
    Integer grantValidityDays,
    String repeatPolicy,
    Integer points,
    Integer referrerPoints,
    Integer referredPoints,
    boolean enabled,
    Long version) {}
