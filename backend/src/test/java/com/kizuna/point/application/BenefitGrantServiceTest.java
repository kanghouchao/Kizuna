package com.kizuna.point.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.point.domain.BenefitRule;
import com.kizuna.point.domain.BenefitRuleDefinition;
import com.kizuna.point.domain.BenefitRuleRepeatPolicy;
import com.kizuna.point.domain.BenefitRuleRepository;
import com.kizuna.point.domain.BenefitRuleType;
import com.kizuna.point.domain.PointEntry;
import com.kizuna.point.domain.PointEntryRepository;
import com.kizuna.point.domain.PointEntryType;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.user.domain.StoreScopeType;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BenefitGrantServiceTest {

  private static final String TIMEZONE = "Asia/Tokyo";
  private static final long MEMBER_ID = 7L;
  private static final long STORE_ID = 3L;
  private static final long RULE_ID = 11L;
  private static final long ACTOR_ID = 9L;
  private static final String ORDER_ID = "order-1";

  @Mock private BenefitRuleRepository benefitRuleRepository;
  @Mock private PointEntryRepository pointEntryRepository;
  @Mock private AppProperties appProperties;

  @InjectMocks private BenefitGrantService benefitGrantService;

  @BeforeEach
  void setUp() {
    lenient().when(appProperties.getTimezone()).thenReturn(TIMEZONE);
  }

  @Test
  @DisplayName("窓の内側で適用店舗の受注は、規則の点数と期限で特典付与を記帳すること")
  void anApplicableRuleIsPostedWithItsPointsAndExpiry() {
    given(rule(BenefitRuleRepeatPolicy.EVERY_TIME, 180, StoreScopeType.SPECIFIC_STORES));

    benefitGrantService.grantVisitBenefits(
        MEMBER_ID, ORDER_ID, STORE_ID, LocalDate.of(2026, 10, 1), ACTOR_ID);

    PointEntry posted = captureSaved();
    assertThat(posted.getEntryType()).isEqualTo(PointEntryType.BENEFIT_GRANT);
    assertThat(posted.getAmount()).isEqualTo(500);
    assertThat(posted.getMemberId()).isEqualTo(MEMBER_ID);
    assertThat(posted.getOrderId()).as("巻き戻しが拾えるよう受注を名乗ること").isEqualTo(ORDER_ID);
    assertThat(posted.getOriginatingStoreId()).isEqualTo(STORE_ID);
    assertThat(posted.getBenefitRuleId()).isEqualTo(RULE_ID);
    assertThat(posted.getActorUserId()).isEqualTo(ACTOR_ID);
    assertThat(posted.getExpiresOn())
        .as("期限は記帳した日から起算する（根拠受注の日ではない）")
        .isEqualTo(LocalDate.now(ZoneId.of(TIMEZONE)).plusDays(180));
  }

  @Test
  @DisplayName("無期限指定の規則が産む付与は期限を持たないこと")
  void anUnlimitedRuleProducesAnUnexpiringLot() {
    given(rule(BenefitRuleRepeatPolicy.EVERY_TIME, null, StoreScopeType.ALL_STORES));

    benefitGrantService.grantVisitBenefits(
        MEMBER_ID, ORDER_ID, STORE_ID, LocalDate.of(2026, 10, 1), ACTOR_ID);

    assertThat(captureSaved().getExpiresOn()).isNull();
  }

  @Test
  @DisplayName("一人一回限りの規則は、既に受益した会員へ二件目の受注で付与しないこと")
  void aOncePerMemberRuleDoesNotFireTwiceForTheSameMember() {
    given(rule(BenefitRuleRepeatPolicy.ONCE_PER_MEMBER, null, StoreScopeType.ALL_STORES));
    when(pointEntryRepository.existsByBenefitRuleIdAndMemberId(RULE_ID, MEMBER_ID))
        .thenReturn(true);

    benefitGrantService.grantVisitBenefits(
        MEMBER_ID, "order-2", STORE_ID, LocalDate.of(2026, 10, 1), ACTOR_ID);

    verify(pointEntryRepository, never()).save(any());
  }

  @Test
  @DisplayName("毎回の規則は、同じ発火事象（同じ受注）では二度目を記帳しないこと")
  void anEveryTimeRuleStillRefusesTheSameFiringEventTwice() {
    given(rule(BenefitRuleRepeatPolicy.EVERY_TIME, null, StoreScopeType.ALL_STORES));
    when(pointEntryRepository.existsByBenefitRuleIdAndMemberIdAndOrderId(
            RULE_ID, MEMBER_ID, ORDER_ID))
        .thenReturn(true);

    benefitGrantService.grantVisitBenefits(
        MEMBER_ID, ORDER_ID, STORE_ID, LocalDate.of(2026, 10, 1), ACTOR_ID);

    verify(pointEntryRepository, never()).save(any());
  }

  @Test
  @DisplayName("毎回の規則は、会員の受益歴ではなく発火事象だけを見ること")
  void anEveryTimeRuleNeverConsultsTheMemberWideHistory() {
    given(rule(BenefitRuleRepeatPolicy.EVERY_TIME, null, StoreScopeType.ALL_STORES));

    benefitGrantService.grantVisitBenefits(
        MEMBER_ID, "order-2", STORE_ID, LocalDate.of(2026, 10, 1), ACTOR_ID);

    verify(pointEntryRepository, never()).existsByBenefitRuleIdAndMemberId(RULE_ID, MEMBER_ID);
    assertThat(captureSaved().getOrderId()).isEqualTo("order-2");
  }

  @Test
  @DisplayName("適用店舗の外で起きた受注には付与しないこと")
  void aRuleDoesNotFireOutsideItsStoreSet() {
    given(rule(BenefitRuleRepeatPolicy.EVERY_TIME, null, StoreScopeType.SPECIFIC_STORES));

    benefitGrantService.grantVisitBenefits(
        MEMBER_ID, ORDER_ID, 99L, LocalDate.of(2026, 10, 1), ACTOR_ID);

    verify(pointEntryRepository, never()).save(any());
  }

  @Test
  @DisplayName("適用期間の窓は根拠受注の日で判じ、記帳する日では判じないこと")
  void theWindowIsJudgedByTheOriginatingOrderDate() {
    given(rule(BenefitRuleRepeatPolicy.EVERY_TIME, null, StoreScopeType.ALL_STORES));

    benefitGrantService.grantVisitBenefits(
        MEMBER_ID, ORDER_ID, STORE_ID, LocalDate.of(2026, 8, 31), ACTOR_ID);

    verify(pointEntryRepository, never()).save(any());
  }

  /** 停用済みの規則は問い合わせの側でも落ちるが、集約の側でも拾わないことを固定する（濾過が二層あることの明示）。 */
  @Test
  @DisplayName("停用済みの規則が問い合わせをすり抜けても付与しないこと")
  void aDeactivatedRuleIsRefusedByTheAggregateToo() {
    BenefitRule deactivated =
        rule(BenefitRuleRepeatPolicy.EVERY_TIME, null, StoreScopeType.ALL_STORES);
    deactivated.deactivate();
    given(deactivated);

    benefitGrantService.grantVisitBenefits(
        MEMBER_ID, ORDER_ID, STORE_ID, LocalDate.of(2026, 10, 1), ACTOR_ID);

    verify(pointEntryRepository, never()).save(any());
  }

  private void given(BenefitRule rule) {
    when(benefitRuleRepository.findByTypeAndEnabledTrue(BenefitRuleType.VISIT))
        .thenReturn(List.of(rule));
  }

  private PointEntry captureSaved() {
    ArgumentCaptor<PointEntry> captor = ArgumentCaptor.forClass(PointEntry.class);
    verify(pointEntryRepository).save(captor.capture());
    return captor.getValue();
  }

  private static BenefitRule rule(
      BenefitRuleRepeatPolicy repeatPolicy, Integer validityDays, StoreScopeType scopeType) {
    BenefitRule rule =
        BenefitRule.define(
            BenefitRuleType.VISIT,
            BenefitRuleDefinition.builder()
                .name("来店ボーナス")
                .storeScopeType(scopeType)
                .storeIds(scopeType == StoreScopeType.ALL_STORES ? Set.of() : Set.of(STORE_ID))
                .effectiveFrom(LocalDate.of(2026, 9, 1))
                .effectiveUntil(LocalDate.of(2026, 12, 31))
                .grantValidityDays(validityDays)
                .repeatPolicy(repeatPolicy)
                .points(500)
                .build());
    ReflectionTestUtils.setField(rule, "id", RULE_ID);
    return rule;
  }
}
