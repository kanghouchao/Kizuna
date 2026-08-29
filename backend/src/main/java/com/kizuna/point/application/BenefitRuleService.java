package com.kizuna.point.application;

import com.kizuna.point.api.dto.BenefitRuleCreateRequest;
import com.kizuna.point.api.dto.BenefitRuleDeactivationRequest;
import com.kizuna.point.api.dto.BenefitRuleMapper;
import com.kizuna.point.api.dto.BenefitRuleResponse;
import com.kizuna.point.api.dto.BenefitRuleSummaryResponse;
import com.kizuna.point.api.dto.BenefitRuleUpdateRequest;
import com.kizuna.point.domain.BenefitRule;
import com.kizuna.point.domain.BenefitRuleRepository;
import com.kizuna.point.domain.InvalidBenefitRuleException;
import com.kizuna.point.domain.StaleBenefitRuleUpdateException;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.IntegrityViolations;
import com.kizuna.shared.exception.NotFoundException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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

  /**
   * 指した店舗が実在しないことの写像。集合表の行は commit まで flush されないので、{@code save} だけでは違反が全域ハンドラの兜底へ落ちて 500 になる（FK
   * 違反は一意違反と違い兜底の 4xx 対象ではない）。画面が選択肢を取ってから提出するまでの削除も、この一本で拾える。
   */
  private static final Map<DbConstraint, java.util.function.Supplier<RuntimeException>>
      STORE_REFERENCE_VIOLATIONS =
          Map.of(
              DbConstraint.FK_T_BENEFIT_RULE_STORES_STORE,
              () -> new InvalidBenefitRuleException("指定された店舗が見つかりません。選択し直してください"));

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
    return benefitRuleMapper.toResponse(persist(rule));
  }

  @Transactional
  public BenefitRuleResponse update(Long id, BenefitRuleUpdateRequest request) {
    BenefitRule rule = find(id);
    // 全量置換なので、開いたまま別の管理者が編集を済ませていると送らなかった項目まで開いた時点の値で
    // 押し戻す。陳腐化した編集フォームの提出は JPA の @Version では捕まらない（再読込後の正当な更新に
    // 見える）ため、応答で往復させた version を明示比対して 409 で拒否する。
    requireExpectedVersion(rule, request.getVersion());
    rule.redefine(benefitRuleMapper.toDefinition(request));
    return benefitRuleMapper.toResponse(persist(rule));
  }

  /**
   * 停用（退場）。再開の口が無い一方通行なので、更新と同じく確認した版を照合する — 承認の画面を開いている間に別の管理者が 内容を書き換えていれば、操作者が見ていない規則を消すことになる。
   */
  @Transactional
  public void deactivate(Long id, BenefitRuleDeactivationRequest request) {
    BenefitRule rule = find(id);
    requireExpectedVersion(rule, request.getVersion());
    rule.deactivate();
  }

  private static void requireExpectedVersion(BenefitRule rule, Long expected) {
    // 陳腐化した編集フォームの提出は JPA の @Version では捕まらない（再読込後の正当な更新に見える）
    // ため、応答で往復させた版を明示比対して 409 で拒否する。
    if (!rule.getVersion().equals(expected)) {
      throw new StaleBenefitRuleUpdateException("他の管理者が更新しました。最新の内容を確認してください");
    }
  }

  /**
   * 保存して flush する。flush を挟むのは二つの理由による — 応答が返す版を確定させること（{@code save} だけでは @Version が commit
   * まで進まず、その応答を次の編集へそのまま渡すと自分の版で 409 になる）と、集合表の外部キー違反を この場で捕まえて 400 へ写すこと。
   */
  private BenefitRule persist(BenefitRule rule) {
    try {
      return benefitRuleRepository.saveAndFlush(rule);
    } catch (DataIntegrityViolationException ex) {
      throw IntegrityViolations.translate(ex, STORE_REFERENCE_VIOLATIONS);
    }
  }

  private BenefitRule find(Long id) {
    return benefitRuleRepository
        .findById(id)
        .orElseThrow(() -> new NotFoundException("特典規則が見つかりません"));
  }
}
