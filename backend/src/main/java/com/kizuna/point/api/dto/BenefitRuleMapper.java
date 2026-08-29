package com.kizuna.point.api.dto;

import com.kizuna.point.domain.BenefitRule;
import com.kizuna.point.domain.BenefitRuleDefinition;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** 特典規則の集約と DTO のマッピングを行う MapStruct マッパー。 */
@Mapper(componentModel = "spring")
public interface BenefitRuleMapper {

  /** 一覧の要約。適用店舗は件数へ畳み、ID の列挙は詳細に譲る。 */
  @Mapping(target = "storeCount", expression = "java(rule.getStoreIds().size())")
  BenefitRuleSummaryResponse toSummary(BenefitRule rule);

  BenefitRuleResponse toResponse(BenefitRule rule);

  BenefitRuleDefinition toDefinition(BenefitRuleCreateRequest request);

  BenefitRuleDefinition toDefinition(BenefitRuleUpdateRequest request);
}
