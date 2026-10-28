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
import com.kizuna.user.application.BusinessAudit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 特典規則の管理。管理画面 限定の操作で、店舗自助の入口は持たない。
 *
 * <p>削除の口を持たないのは、後続の付与仕訳が規則を FK RESTRICT で指し返すためである。退場は停用で表し、停用は一方通行にする。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BenefitRuleService {

  private final BenefitRuleRepository benefitRuleRepository;
  private final BenefitRuleMapper benefitRuleMapper;
  private final BusinessAudit businessAudit;

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
        persist(BenefitRule.define(request.getType(), benefitRuleMapper.toDefinition(request)));
    audit("BENEFIT_RULE_CREATED", rule.getId(), Map.of("exists", "false"), snapshot(rule));
    return benefitRuleMapper.toResponse(rule);
  }

  @Transactional
  public BenefitRuleResponse update(Long id, BenefitRuleUpdateRequest request) {
    BenefitRule rule = find(id);
    // 全量置換なので、開いたまま別の管理者が編集を済ませていると送らなかった項目まで開いた時点の値で
    // 押し戻す。陳腐化した編集フォームの提出は JPA の @Version では捕まらない（再読込後の正当な更新に
    // 見える）ため、応答で往復させた version を明示比対して 409 で拒否する。
    requireExpectedVersion(rule, request.getVersion());
    var before = snapshot(rule);
    String oldName = rule.getName();
    rule.redefine(benefitRuleMapper.toDefinition(request));
    persist(rule);
    var after = snapshot(rule);
    var changed = changedFields(before, after);
    if (!Objects.equals(oldName, rule.getName())) {
      changed.add("name");
    }
    if (!changed.isEmpty()) {
      after.put("changed_fields", String.join(",", changed));
      audit("BENEFIT_RULE_UPDATED", id, before, after);
    }
    return benefitRuleMapper.toResponse(rule);
  }

  /**
   * 停用（退場）。再開の口が無い一方通行なので、更新と同じく確認した版を照合する — 承認の画面を開いている間に別の管理者が 内容を書き換えていれば、操作者が見ていない規則を消すことになる。
   */
  @Transactional
  public void deactivate(Long id, BenefitRuleDeactivationRequest request) {
    BenefitRule rule = find(id);
    requireExpectedVersion(rule, request.getVersion());
    var before = snapshot(rule);
    rule.deactivate();
    persist(rule);
    audit("BENEFIT_RULE_DEACTIVATED", id, before, snapshot(rule));
  }

  /** 特典規則はどの店舗にも属さないグループ共通の設定なので、監査の店舗は常に null。 */
  private void audit(
      String action, Long id, Map<String, String> before, Map<String, String> after) {
    businessAudit.recordCurrent(null, action, "BENEFIT_RULE", String.valueOf(id), before, after);
  }

  /** 名前は自由文なので snapshot に載せず、更新では変更項目名（name）だけで表す。 */
  private static Map<String, String> snapshot(BenefitRule rule) {
    var values = new LinkedHashMap<String, String>();
    values.put("exists", "true");
    values.put("active", String.valueOf(rule.getEnabled()));
    values.put("version", String.valueOf(rule.getVersion()));
    values.put("type", rule.getType().name());
    values.put("store_scope_type", rule.getStoreScopeType().name());
    values.put("store_ids", BusinessAudit.ids(rule.getStoreIds()));
    values.put("effective_from", Objects.toString(rule.getEffectiveFrom(), ""));
    values.put("effective_until", Objects.toString(rule.getEffectiveUntil(), ""));
    values.put("grant_validity_days", Objects.toString(rule.getGrantValidityDays(), ""));
    values.put("repeat_policy", rule.getRepeatPolicy().name());
    values.put("points", Objects.toString(rule.getPoints(), ""));
    values.put("referrer_points", Objects.toString(rule.getReferrerPoints(), ""));
    values.put("referred_points", Objects.toString(rule.getReferredPoints(), ""));
    return values;
  }

  private static List<String> changedFields(Map<String, String> before, Map<String, String> after) {
    var changed = new ArrayList<String>();
    after.forEach(
        (key, value) -> {
          if (!"version".equals(key) && !value.equals(before.get(key))) changed.add(key);
        });
    return changed;
  }

  private static void requireExpectedVersion(BenefitRule rule, Long expected) {
    // 陳腐化した編集フォームの提出は JPA の @Version では捕まらない（再読込後の正当な更新に見える）
    // ため、応答で往復させた版を明示比対して 409 で拒否する。
    if (!rule.getVersion().equals(expected)) {
      throw new StaleBenefitRuleUpdateException("他の管理者が更新しました。最新の内容を確認してください");
    }
  }

  /** 応答に更新後の版を返し、次回の編集で自分の更新と競合しないようにする。 */
  private BenefitRule persist(BenefitRule rule) {
    return IntegrityViolations.translateOnFailure(
        () -> benefitRuleRepository.saveAndFlush(rule), STORE_REFERENCE_VIOLATIONS);
  }

  private BenefitRule find(Long id) {
    return benefitRuleRepository
        .findById(id)
        .orElseThrow(() -> new NotFoundException("特典規則が見つかりません"));
  }
}
