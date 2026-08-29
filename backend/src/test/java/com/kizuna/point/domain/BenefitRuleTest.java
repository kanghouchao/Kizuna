package com.kizuna.point.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.user.domain.StoreScopeType;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BenefitRuleTest {

  private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
  private static final LocalDate UNTIL = LocalDate.of(2026, 12, 31);

  @Test
  @DisplayName("来店規則は固定点数と二つの期間を保持し、有効な状態で生まれること")
  void visitRuleHoldsFiveElements() {
    BenefitRule rule =
        BenefitRule.define(
            BenefitRuleType.VISIT,
            definition()
                .storeScopeType(StoreScopeType.SPECIFIC_STORES)
                .storeIds(Set.of(3L, 5L))
                .effectiveFrom(FROM)
                .effectiveUntil(UNTIL)
                .grantValidityDays(180)
                .repeatPolicy(BenefitRuleRepeatPolicy.EVERY_TIME)
                .points(500)
                .build());

    assertThat(rule.getType()).isEqualTo(BenefitRuleType.VISIT);
    assertThat(rule.getStoreScopeType()).isEqualTo(StoreScopeType.SPECIFIC_STORES);
    assertThat(rule.getStoreIds()).containsExactlyInAnyOrder(3L, 5L);
    assertThat(rule.getEffectiveFrom()).isEqualTo(FROM);
    assertThat(rule.getEffectiveUntil()).isEqualTo(UNTIL);
    assertThat(rule.getGrantValidityDays()).isEqualTo(180);
    assertThat(rule.getRepeatPolicy()).isEqualTo(BenefitRuleRepeatPolicy.EVERY_TIME);
    assertThat(rule.getPoints()).isEqualTo(500);
    assertThat(rule.getReferrerPoints()).isNull();
    assertThat(rule.getReferredPoints()).isNull();
    assertThat(rule.getEnabled()).isTrue();
  }

  @Test
  @DisplayName("適用期間と付与ポイント有効期間はいずれも省略できること")
  void bothPeriodsAreOptional() {
    BenefitRule rule = BenefitRule.define(BenefitRuleType.VISIT, definition().points(100).build());

    assertThat(rule.getEffectiveFrom()).isNull();
    assertThat(rule.getEffectiveUntil()).isNull();
    assertThat(rule.getGrantValidityDays()).isNull();
  }

  @Test
  @DisplayName("紹介規則は紹介者点数・被紹介者点数の二値を保持すること")
  void referralRuleHoldsBothPointValues() {
    BenefitRule rule =
        BenefitRule.define(
            BenefitRuleType.REFERRAL,
            definition().points(null).referrerPoints(1000).referredPoints(500).build());

    assertThat(rule.getPoints()).isNull();
    assertThat(rule.getReferrerPoints()).isEqualTo(1000);
    assertThat(rule.getReferredPoints()).isEqualTo(500);
  }

  @Test
  @DisplayName("紹介規則が片方の点数しか持たない定義は拒まれること")
  void referralRuleWithoutBothPointValuesIsRejected() {
    assertThatThrownBy(
            () ->
                BenefitRule.define(
                    BenefitRuleType.REFERRAL,
                    definition().points(null).referrerPoints(1000).build()))
        .isInstanceOf(InvalidBenefitRuleException.class)
        .hasMessageContaining("被紹介者");
  }

  @Test
  @DisplayName("紹介以外の種別に紹介の二値を持たせられないこと")
  void nonReferralRuleCannotCarryReferralPoints() {
    assertThatThrownBy(
            () ->
                BenefitRule.define(
                    BenefitRuleType.VISIT,
                    definition().points(100).referrerPoints(1000).referredPoints(500).build()))
        .isInstanceOf(InvalidBenefitRuleException.class)
        .hasMessageContaining("紹介以外");
  }

  @Test
  @DisplayName("紹介以外の種別は固定点数を必ず持つこと")
  void nonReferralRuleRequiresPoints() {
    assertThatThrownBy(
            () -> BenefitRule.define(BenefitRuleType.LOGIN, definition().points(null).build()))
        .isInstanceOf(InvalidBenefitRuleException.class)
        .hasMessageContaining("付与ポイント");
  }

  @Test
  @DisplayName("0 以下の付与点数は拒まれること")
  void nonPositivePointsAreRejected() {
    assertThatThrownBy(
            () -> BenefitRule.define(BenefitRuleType.VISIT, definition().points(0).build()))
        .isInstanceOf(InvalidBenefitRuleException.class)
        .hasMessageContaining("1 以上");
  }

  @Test
  @DisplayName("紹介規則は一人一回限りを取れないこと")
  void referralRuleCannotBeOncePerMember() {
    assertThatThrownBy(
            () ->
                BenefitRule.define(
                    BenefitRuleType.REFERRAL,
                    definition()
                        .repeatPolicy(BenefitRuleRepeatPolicy.ONCE_PER_MEMBER)
                        .points(null)
                        .referrerPoints(1000)
                        .referredPoints(500)
                        .build()))
        .isInstanceOf(InvalidBenefitRuleException.class)
        .hasMessageContaining("紹介規則は毎回");
  }

  @Test
  @DisplayName("ログイン規則は全店舗しか取れないこと")
  void loginRuleMustBeAllStores() {
    assertThatThrownBy(
            () ->
                BenefitRule.define(
                    BenefitRuleType.LOGIN,
                    definition()
                        .storeScopeType(StoreScopeType.SPECIFIC_STORES)
                        .storeIds(Set.of(3L))
                        .points(100)
                        .build()))
        .isInstanceOf(InvalidBenefitRuleException.class)
        .hasMessageContaining("ログイン");
  }

  @Test
  @DisplayName("店舗集合を指定する規則は少なくとも 1 店舗を持つこと")
  void specificStoresRequiresAtLeastOneStore() {
    assertThatThrownBy(
            () ->
                BenefitRule.define(
                    BenefitRuleType.VISIT,
                    definition()
                        .storeScopeType(StoreScopeType.SPECIFIC_STORES)
                        .storeIds(Set.of())
                        .points(100)
                        .build()))
        .isInstanceOf(InvalidBenefitRuleException.class)
        .hasMessageContaining("1 つの店舗");
  }

  @Test
  @DisplayName("全店舗の規則に個別店舗を添えられないこと")
  void allStoresRejectsIndividualStores() {
    assertThatThrownBy(
            () ->
                BenefitRule.define(
                    BenefitRuleType.VISIT,
                    definition()
                        .storeScopeType(StoreScopeType.ALL_STORES)
                        .storeIds(Set.of(3L))
                        .points(100)
                        .build()))
        .isInstanceOf(InvalidBenefitRuleException.class)
        .hasMessageContaining("全店舗");
  }

  @Test
  @DisplayName("適用期間の開始が終了より後の定義は拒まれること")
  void invertedEffectivePeriodIsRejected() {
    assertThatThrownBy(
            () ->
                BenefitRule.define(
                    BenefitRuleType.VISIT,
                    definition().effectiveFrom(UNTIL).effectiveUntil(FROM).points(100).build()))
        .isInstanceOf(InvalidBenefitRuleException.class)
        .hasMessageContaining("適用期間");
  }

  @Test
  @DisplayName("0 以下の付与ポイント有効期間は拒まれること")
  void nonPositiveGrantValidityIsRejected() {
    assertThatThrownBy(
            () ->
                BenefitRule.define(
                    BenefitRuleType.VISIT, definition().grantValidityDays(0).points(100).build()))
        .isInstanceOf(InvalidBenefitRuleException.class)
        .hasMessageContaining("有効期間");
  }

  @Test
  @DisplayName("規則名の無い定義は拒まれること")
  void blankNameIsRejected() {
    assertThatThrownBy(
            () ->
                BenefitRule.define(
                    BenefitRuleType.VISIT, definition().name("  ").points(100).build()))
        .isInstanceOf(InvalidBenefitRuleException.class)
        .hasMessageContaining("規則名");
  }

  @Test
  @DisplayName("再定義は種別を動かさずに残りの五要素を置き換えること")
  void redefineReplacesEverythingButType() {
    BenefitRule rule = BenefitRule.define(BenefitRuleType.VISIT, definition().points(100).build());

    rule.redefine(
        definition()
            .name("年末キャンペーン")
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(7L))
            .effectiveFrom(FROM)
            .effectiveUntil(UNTIL)
            .grantValidityDays(30)
            .repeatPolicy(BenefitRuleRepeatPolicy.ONCE_PER_MEMBER)
            .points(300)
            .build());

    assertThat(rule.getType()).isEqualTo(BenefitRuleType.VISIT);
    assertThat(rule.getName()).isEqualTo("年末キャンペーン");
    assertThat(rule.getStoreIds()).containsExactly(7L);
    assertThat(rule.getRepeatPolicy()).isEqualTo(BenefitRuleRepeatPolicy.ONCE_PER_MEMBER);
    assertThat(rule.getPoints()).isEqualTo(300);
  }

  @Test
  @DisplayName("再定義も構築と同じ不変条件で検証されること")
  void redefineValidatesTheSameInvariants() {
    BenefitRule rule = BenefitRule.define(BenefitRuleType.LOGIN, definition().points(100).build());

    assertThatThrownBy(
            () ->
                rule.redefine(
                    definition()
                        .storeScopeType(StoreScopeType.SPECIFIC_STORES)
                        .storeIds(Set.of(3L))
                        .points(100)
                        .build()))
        .isInstanceOf(InvalidBenefitRuleException.class)
        .hasMessageContaining("ログイン");
  }

  @Test
  @DisplayName("停用した規則は enabled が falseになり、二度目の停用は拒まれること")
  void deactivationIsOneWayAndNotIdempotent() {
    BenefitRule rule = BenefitRule.define(BenefitRuleType.VISIT, definition().points(100).build());

    rule.deactivate();

    assertThat(rule.getEnabled()).isFalse();
    assertThatThrownBy(rule::deactivate)
        .isInstanceOf(InvalidBenefitRuleException.class)
        .hasMessageContaining("停用済み");
  }

  @Test
  @DisplayName("停用した規則は編集も受け付けないこと")
  void deactivatedRuleCannotBeRedefined() {
    BenefitRule rule = BenefitRule.define(BenefitRuleType.VISIT, definition().points(100).build());
    rule.deactivate();

    assertThatThrownBy(() -> rule.redefine(definition().points(200).build()))
        .isInstanceOf(InvalidBenefitRuleException.class)
        .hasMessageContaining("停用済み");
  }

  private static BenefitRuleDefinition.BenefitRuleDefinitionBuilder definition() {
    return BenefitRuleDefinition.builder()
        .name("来店ボーナス")
        .storeScopeType(StoreScopeType.ALL_STORES)
        .storeIds(Set.of())
        .repeatPolicy(BenefitRuleRepeatPolicy.EVERY_TIME);
  }
}
