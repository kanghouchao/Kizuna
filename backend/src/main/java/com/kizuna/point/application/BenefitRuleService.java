package com.kizuna.point.application;

import com.kizuna.point.api.dto.BenefitRuleCreateRequest;
import com.kizuna.point.api.dto.BenefitRuleMapper;
import com.kizuna.point.api.dto.BenefitRuleResponse;
import com.kizuna.point.api.dto.BenefitRuleSummaryResponse;
import com.kizuna.point.api.dto.BenefitRuleUpdateRequest;
import com.kizuna.point.domain.BenefitRule;
import com.kizuna.point.domain.BenefitRuleRepository;
import com.kizuna.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 特典規則の管理。プラットフォーム console 限定の操作で、店舗自助の入口は持たない。
 *
 * <p>削除の口を持たないのは、後続の付与仕訳が規則を FK RESTRICT で指し返すためである。退場は停用で表し、停用は一方通行にする。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BenefitRuleService {

  private final BenefitRuleRepository benefitRuleRepository;
  private final BenefitRuleMapper benefitRuleMapper;

  /** 停用済みも含めた全規則を新しい順に返す。停用が退場を表すので、一覧から消えるものは無い。 */
  public Page<BenefitRuleSummaryResponse> list(Pageable pageable) {
    return benefitRuleRepository
        .findAllByOrderByCreatedAtDescIdDesc(pageable)
        .map(benefitRuleMapper::toSummary);
  }

  public BenefitRuleResponse get(Long id) {
    return benefitRuleMapper.toResponse(find(id));
  }

  @Transactional
  public BenefitRuleResponse create(BenefitRuleCreateRequest request) {
    BenefitRule rule =
        BenefitRule.define(request.getType(), benefitRuleMapper.toDefinition(request));
    return benefitRuleMapper.toResponse(benefitRuleRepository.save(rule));
  }

  @Transactional
  public BenefitRuleResponse update(Long id, BenefitRuleUpdateRequest request) {
    BenefitRule rule = find(id);
    rule.redefine(benefitRuleMapper.toDefinition(request));
    return benefitRuleMapper.toResponse(benefitRuleRepository.save(rule));
  }

  @Transactional
  public void deactivate(Long id) {
    find(id).deactivate();
  }

  private BenefitRule find(Long id) {
    return benefitRuleRepository
        .findById(id)
        .orElseThrow(() -> new NotFoundException("特典規則が見つかりません"));
  }
}
