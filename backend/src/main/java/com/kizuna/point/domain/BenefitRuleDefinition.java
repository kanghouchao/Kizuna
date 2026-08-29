package com.kizuna.point.domain;

import com.kizuna.user.domain.StoreScopeType;
import java.time.LocalDate;
import java.util.Set;
import lombok.Builder;

/**
 * 特典規則の定義内容。種別を除く五要素の全量で、作成と再定義が同じ形を受ける。
 *
 * <p>種別を含めないのは、種別が作成後に動かないためである（付与仕訳が規則を指し返した後に種別を翻すと、記帳済みの付与の取消方法が遡って変わる）。
 *
 * <p>点数は種別で形が変わる — 紹介は {@code referrerPoints} / {@code referredPoints} の二値を、それ以外は {@code points} の
 * 一値を持つ（{@link BenefitRule} が検証する）。
 */
@Builder
public record BenefitRuleDefinition(
    String name,
    StoreScopeType storeScopeType,
    Set<Long> storeIds,
    LocalDate effectiveFrom,
    LocalDate effectiveUntil,
    Integer grantValidityDays,
    BenefitRuleRepeatPolicy repeatPolicy,
    Integer points,
    Integer referrerPoints,
    Integer referredPoints) {}
