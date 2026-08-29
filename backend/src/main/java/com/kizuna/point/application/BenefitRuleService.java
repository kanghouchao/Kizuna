package com.kizuna.point.application;

import com.kizuna.point.api.dto.BenefitRuleCreateRequest;
import com.kizuna.point.api.dto.BenefitRuleMapper;
import com.kizuna.point.api.dto.BenefitRuleResponse;
import com.kizuna.point.api.dto.BenefitRuleSummaryResponse;
import com.kizuna.point.api.dto.BenefitRuleUpdateRequest;
import com.kizuna.point.domain.BenefitRule;
import com.kizuna.point.domain.BenefitRuleDefinition;
import com.kizuna.point.domain.BenefitRuleRepository;
import com.kizuna.point.domain.InvalidBenefitRuleException;
import com.kizuna.point.domain.StaleBenefitRuleUpdateException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import java.util.Set;
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
  private final StoreExistenceCheck storeExistenceCheck;

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
    BenefitRuleDefinition definition = benefitRuleMapper.toDefinition(request);
    requireExistingStores(definition);
    BenefitRule rule = BenefitRule.define(request.getType(), definition);
    return benefitRuleMapper.toResponse(benefitRuleRepository.save(rule));
  }

  @Transactional
  public BenefitRuleResponse update(Long id, BenefitRuleUpdateRequest request) {
    BenefitRule rule = find(id);
    // 全量置換なので、開いたまま別の管理者が編集を済ませていると送らなかった項目まで開いた時点の値で
    // 押し戻す。陳腐化した編集フォームの提出は JPA の @Version では捕まらない（再読込後の正当な更新に
    // 見える）ため、応答で往復させた version を明示比対して 409 で拒否する。
    if (!rule.getVersion().equals(request.getVersion())) {
      throw new StaleBenefitRuleUpdateException("他の管理者が更新しました。最新の内容を確認してください");
    }
    BenefitRuleDefinition definition = benefitRuleMapper.toDefinition(request);
    requireExistingStores(definition);
    rule.redefine(definition);
    return benefitRuleMapper.toResponse(benefitRuleRepository.save(rule));
  }

  @Transactional
  public void deactivate(Long id) {
    find(id).deactivate();
  }

  /**
   * 指定された店舗が実在することを保存前に確かめる。
   *
   * <p>画面が選択肢を取ってから提出するまでに店舗が消えることがあり、集約は集合が非空であることしか見ない。 素通しすると外部キー違反が全域ハンドラの兜底へ落ち、直せる入力の誤りが 500
   * で返る。
   */
  private void requireExistingStores(BenefitRuleDefinition definition) {
    Set<Long> storeIds = definition.storeIds();
    if (storeIds == null) {
      return;
    }
    for (Long storeId : storeIds) {
      if (storeId == null || !storeExistenceCheck.exists(storeId)) {
        throw new InvalidBenefitRuleException("指定された店舗が見つかりません。選択し直してください");
      }
    }
  }

  private BenefitRule find(Long id) {
    return benefitRuleRepository
        .findById(id)
        .orElseThrow(() -> new NotFoundException("特典規則が見つかりません"));
  }
}
