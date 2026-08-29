package com.kizuna.point.domain;

import com.kizuna.shared.persistence.BaseEntity;
import com.kizuna.user.domain.StoreScopeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

/**
 * 紹介・ログイン・来店を契機とする追加ポイント付与の規則。五要素（適用組織/店舗・条件・期間・重複可否・取消方法）を明示して定義する独立集約。
 *
 * <p>適用組織/店舗は<b>発火側</b>の絞り込み（どの店舗での事象が条件を満たすか）であって、付与されたポイントの利用範囲ではない — ポイントは常にプラットフォーム級である（ADR
 * 0006）。取消方法は種別から導くため列を持たない。
 *
 * <p>不変条件（構築時と再定義時に検証、違反は 400 系ドメイン例外 {@link InvalidBenefitRuleException}）:
 *
 * <ol>
 *   <li>ログイン規則は全店舗のみ。発火事象が店舗文脈を持たないため、店舗集合で絞る意味が無い。
 *   <li>店舗集合は {@code SPECIFIC_STORES} なら非空、{@code ALL_STORES} なら空。
 *   <li>点数は種別が形を決める — 紹介は紹介者・被紹介者の二値、それ以外は一値で、いずれも 1 以上。
 *   <li>適用期間は開始 ≦ 終了。付与ポイント有効期間は 1 以上。
 *   <li>紹介規則は毎回のみ。一回性は条件側（被紹介者の初回受注）にあり、紹介者は紹介した人数ぶん受益する。
 * </ol>
 *
 * <p>規則は停用で退場し、削除しない（付与仕訳が FK RESTRICT で指し返す）。停用は一方通行で、停用済みは再定義も受け付けない。
 */
@Entity
@Table(name = "t_benefit_rules")
@Getter
@NoArgsConstructor
public class BenefitRule extends BaseEntity {

  @Column(nullable = false, length = 100)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 20)
  private BenefitRuleType type;

  /** 発火側の絞り込み種別。授権（{@code PlatformUser}）と同じ語彙を共有するが、意味は「どの店舗での事象を拾うか」である。 */
  @Enumerated(EnumType.STRING)
  @Column(name = "store_scope_type", nullable = false, length = 20)
  private StoreScopeType storeScopeType;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "t_benefit_rule_stores", joinColumns = @JoinColumn(name = "rule_id"))
  @Column(name = "store_id")
  @BatchSize(size = 25)
  private Set<Long> storeIds = new HashSet<>();

  /** 規則の適用期間（発火の窓）。null は常設。 */
  @Column(name = "effective_from")
  private LocalDate effectiveFrom;

  @Column(name = "effective_until")
  private LocalDate effectiveUntil;

  /** 付与ポイントの有効期間（日数）。null は無期限。 */
  @Column(name = "grant_validity_days")
  private Integer grantValidityDays;

  @Enumerated(EnumType.STRING)
  @Column(name = "repeat_policy", nullable = false, length = 20)
  private BenefitRuleRepeatPolicy repeatPolicy;

  /** 紹介以外の種別の固定付与点数。 */
  @Column(name = "points")
  private Integer points;

  @Column(name = "referrer_points")
  private Integer referrerPoints;

  @Column(name = "referred_points")
  private Integer referredPoints;

  @Column(nullable = false)
  private Boolean enabled = true;

  private BenefitRule(BenefitRuleType type, BenefitRuleDefinition definition) {
    if (type == null) {
      throw new InvalidBenefitRuleException("種別は必須です");
    }
    this.type = type;
    assign(definition);
  }

  /** 規則を定義する。種別はここでだけ決まり、以後動かない。 */
  public static BenefitRule define(BenefitRuleType type, BenefitRuleDefinition definition) {
    return new BenefitRule(type, definition);
  }

  /** 種別以外の五要素を全量で置き換える。停用済みの規則は退場済みなので受け付けない。 */
  public void redefine(BenefitRuleDefinition definition) {
    if (!this.enabled) {
      throw new InvalidBenefitRuleException("停用済みの規則は編集できません");
    }
    assign(definition);
  }

  /** 停用して退場させる。再開の口は無く、二度目は明示的に撥ねる。 */
  public void deactivate() {
    if (!this.enabled) {
      throw new InvalidBenefitRuleException("停用済みの規則です");
    }
    this.enabled = false;
  }

  private void assign(BenefitRuleDefinition definition) {
    if (definition == null) {
      throw new InvalidBenefitRuleException("規則の定義は必須です");
    }
    Set<Long> stores = definition.storeIds() == null ? Set.of() : definition.storeIds();
    validateName(definition.name());
    validateScope(this.type, definition.storeScopeType(), stores);
    validatePeriods(
        definition.effectiveFrom(), definition.effectiveUntil(), definition.grantValidityDays());
    validatePoints(
        this.type, definition.points(), definition.referrerPoints(), definition.referredPoints());
    validateRepeatPolicy(this.type, definition.repeatPolicy());

    this.name = definition.name().trim();
    this.storeScopeType = definition.storeScopeType();
    this.storeIds = new HashSet<>(stores);
    this.effectiveFrom = definition.effectiveFrom();
    this.effectiveUntil = definition.effectiveUntil();
    this.grantValidityDays = definition.grantValidityDays();
    this.repeatPolicy = definition.repeatPolicy();
    this.points = definition.points();
    this.referrerPoints = definition.referrerPoints();
    this.referredPoints = definition.referredPoints();
  }

  private static void validateName(String name) {
    if (name == null || name.isBlank()) {
      throw new InvalidBenefitRuleException("規則名は必須です");
    }
  }

  private static void validateScope(
      BenefitRuleType type, StoreScopeType storeScopeType, Set<Long> stores) {
    if (storeScopeType == null) {
      throw new InvalidBenefitRuleException("適用店舗の種別は必須です");
    }
    if (type == BenefitRuleType.LOGIN && storeScopeType != StoreScopeType.ALL_STORES) {
      throw new InvalidBenefitRuleException("ログイン規則は全店舗のみを取れます");
    }
    if (storeScopeType == StoreScopeType.SPECIFIC_STORES && stores.isEmpty()) {
      throw new InvalidBenefitRuleException("店舗集合の規則には少なくとも 1 つの店舗が必要です");
    }
    // Set.of(...) は contains(null) で NPE を投げるので、走査で確かめる。
    if (stores.stream().anyMatch(Objects::isNull)) {
      throw new InvalidBenefitRuleException("適用店舗に空の指定は混ぜられません");
    }
    if (storeScopeType == StoreScopeType.ALL_STORES && !stores.isEmpty()) {
      throw new InvalidBenefitRuleException("全店舗の規則に個別店舗を指定できません");
    }
  }

  private static void validateRepeatPolicy(
      BenefitRuleType type, BenefitRuleRepeatPolicy repeatPolicy) {
    if (repeatPolicy == null) {
      throw new InvalidBenefitRuleException("重複可否は必須です");
    }
    // 紹介の一回性は条件側（被紹介者の初回有料受注）にあり、紹介者は紹介した人数ぶん受益する。
    // 一人一回限りを許すと、記帳側が重複可否を見た瞬間に二人目以降の紹介が黙って無報酬になる。
    if (type == BenefitRuleType.REFERRAL && repeatPolicy != BenefitRuleRepeatPolicy.EVERY_TIME) {
      throw new InvalidBenefitRuleException("紹介規則は毎回のみを取れます");
    }
  }

  private static void validatePeriods(
      LocalDate effectiveFrom, LocalDate effectiveUntil, Integer grantValidityDays) {
    if (effectiveFrom != null && effectiveUntil != null && effectiveFrom.isAfter(effectiveUntil)) {
      throw new InvalidBenefitRuleException("適用期間の開始は終了以前である必要があります");
    }
    if (grantValidityDays != null && grantValidityDays < 1) {
      throw new InvalidBenefitRuleException("付与ポイントの有効期間は 1 日以上である必要があります");
    }
  }

  private static void validatePoints(
      BenefitRuleType type, Integer points, Integer referrerPoints, Integer referredPoints) {
    if (type == BenefitRuleType.REFERRAL) {
      if (points != null) {
        throw new InvalidBenefitRuleException("紹介規則は紹介者点数・被紹介者点数で付与量を表します");
      }
      requirePositive(referrerPoints, "紹介者点数");
      requirePositive(referredPoints, "被紹介者点数");
      return;
    }
    if (referrerPoints != null || referredPoints != null) {
      throw new InvalidBenefitRuleException("紹介以外の規則に紹介者点数・被紹介者点数を指定できません");
    }
    requirePositive(points, "付与ポイント");
  }

  private static void requirePositive(Integer value, String label) {
    if (value == null) {
      throw new InvalidBenefitRuleException(label + "は必須です");
    }
    if (value < 1) {
      throw new InvalidBenefitRuleException(label + "は 1 以上である必要があります");
    }
  }
}
