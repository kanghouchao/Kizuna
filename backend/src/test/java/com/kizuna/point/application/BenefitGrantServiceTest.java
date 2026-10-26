package com.kizuna.point.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.member.application.MemberLookupService;
import com.kizuna.member.application.MemberLookupService.MemberLookup;
import com.kizuna.point.domain.BenefitRule;
import com.kizuna.point.domain.BenefitRuleDefinition;
import com.kizuna.point.domain.BenefitRuleRepeatPolicy;
import com.kizuna.point.domain.BenefitRuleRepository;
import com.kizuna.point.domain.BenefitRuleType;
import com.kizuna.point.domain.PointEntry;
import com.kizuna.point.domain.PointEntryRepository;
import com.kizuna.point.domain.PointEntryType;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.user.domain.StoreScopeType;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class BenefitGrantServiceTest {

  private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");

  /** 業務時間帯の 2026-10-26 12:00。 */
  private static final Instant NOON = Instant.parse("2026-10-26T03:00:00Z");

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);
  private static final long MEMBER_ID = 7L;
  private static final long USER_ID = 70L;
  private static final long STORE_ID = 3L;
  private static final long RULE_ID = 11L;
  private static final long ACTOR_ID = 9L;
  private static final String ORDER_ID = "order-1";

  @Mock private BenefitRuleRepository benefitRuleRepository;
  @Mock private PointEntryRepository pointEntryRepository;
  @Mock private MemberLookupService memberLookupService;

  private BenefitGrantService benefitGrantService;

  @BeforeEach
  void setUp() {
    benefitGrantService = serviceAt(NOON);
  }

  /** 取引管理は素の mock — TransactionTemplate は getTransaction が null でも本体を実行し commit/rollback を呼ぶだけ。 */
  private BenefitGrantService serviceAt(Instant now) {
    return new BenefitGrantService(
        benefitRuleRepository,
        pointEntryRepository,
        memberLookupService,
        Clock.fixed(now, ZONE),
        mock(PlatformTransactionManager.class));
  }

  @Test
  @DisplayName("窓の内側で適用店舗の受注は、規則の点数と期限で特典付与を記帳すること")
  void anApplicableRuleIsPostedWithItsPointsAndExpiry() {
    given(rule(BenefitRuleRepeatPolicy.EVERY_TIME, 180, StoreScopeType.SPECIFIC_STORES));

    long granted =
        benefitGrantService.grantVisitBenefits(
            MEMBER_ID, ORDER_ID, STORE_ID, LocalDate.of(2026, 10, 1), ACTOR_ID);

    assertThat(granted).as("記帳した合計を返すこと（申領の応答がこの値を足す）").isEqualTo(500);
    PointEntry posted = captureSaved();
    assertThat(posted.getEntryType()).isEqualTo(PointEntryType.BENEFIT_GRANT);
    assertThat(posted.getAmount()).isEqualTo(500);
    assertThat(posted.getMemberId()).isEqualTo(MEMBER_ID);
    assertThat(posted.getOrderId()).as("巻き戻しが拾えるよう受注を名乗ること").isEqualTo(ORDER_ID);
    assertThat(posted.getOriginatingStoreId()).isEqualTo(STORE_ID);
    assertThat(posted.getBenefitRuleId()).isEqualTo(RULE_ID);
    assertThat(posted.getActorUserId()).isEqualTo(ACTOR_ID);
    assertThat(posted.getExpiresOn())
        .as("期限は記帳した日から起算し、その日を含めて数える（根拠受注の日ではない）")
        .isEqualTo(TODAY.plusDays(179));
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

    long granted =
        benefitGrantService.grantVisitBenefits(
            MEMBER_ID, "order-2", STORE_ID, LocalDate.of(2026, 10, 1), ACTOR_ID);

    assertThat(granted).as("積まないなら合計も 0").isZero();
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

  @Test
  @DisplayName("重ねて当たった規則の合計が int を超えても、返す合計が回り込まないこと")
  void theStackedTotalDoesNotWrapAround() {
    // 規則は排他ではなく重ねて当たり、1 件の点数は int の上限までしか縛られていない。合計を int で
    // 持つと回り込んで負になり、申領の応答が「付与はありません」へ落ちる（行はすべて積まれているのに）。
    when(benefitRuleRepository.findByTypeAndEnabledTrue(BenefitRuleType.VISIT))
        .thenReturn(
            List.of(
                rule(
                    BenefitRuleRepeatPolicy.EVERY_TIME,
                    null,
                    StoreScopeType.ALL_STORES,
                    Integer.MAX_VALUE,
                    RULE_ID),
                rule(
                    BenefitRuleRepeatPolicy.EVERY_TIME,
                    null,
                    StoreScopeType.ALL_STORES,
                    Integer.MAX_VALUE,
                    RULE_ID + 1)));

    long granted =
        benefitGrantService.grantVisitBenefits(
            MEMBER_ID, ORDER_ID, STORE_ID, LocalDate.of(2026, 10, 1), ACTOR_ID);

    assertThat(granted).isEqualTo(2L * Integer.MAX_VALUE);
    verify(pointEntryRepository, times(2)).save(any());
  }

  @Test
  @DisplayName("有効な一日一回のログイン規則は、会員の認証成功で付与日付きの特典付与を記帳すること")
  void aLoginRuleIsPostedWithTheGrantDate() {
    givenMember();
    givenLogin(loginRule(BenefitRuleRepeatPolicy.ONCE_PER_DAY, 30));

    benefitGrantService.grantLoginBenefits(USER_ID);

    PointEntry posted = captureFlushed();
    assertThat(posted.getEntryType()).isEqualTo(PointEntryType.BENEFIT_GRANT);
    assertThat(posted.getMemberId()).isEqualTo(MEMBER_ID);
    assertThat(posted.getAmount()).isEqualTo(100);
    assertThat(posted.getBenefitRuleId()).isEqualTo(RULE_ID);
    assertThat(posted.getGrantedOn()).isEqualTo(TODAY);
    assertThat(posted.getOrderId()).isNull();
    assertThat(posted.getOriginatingStoreId()).isNull();
    assertThat(posted.getExpiresOn()).isEqualTo(TODAY.plusDays(29));
  }

  @Test
  @DisplayName("一日一回の規則は、同じ暦日に既に受益した会員へ二件目を記帳しないこと")
  void aOncePerDayRuleDoesNotFireTwiceOnTheSameDay() {
    givenMember();
    givenLogin(loginRule(BenefitRuleRepeatPolicy.ONCE_PER_DAY, null));
    when(pointEntryRepository.existsByBenefitRuleIdAndMemberIdAndGrantedOn(
            RULE_ID, MEMBER_ID, TODAY))
        .thenReturn(true);

    benefitGrantService.grantLoginBenefits(USER_ID);

    verify(pointEntryRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("一人一回限りのログイン規則は、日を跨いでも受益歴のある会員へ記帳しないこと")
  void aOncePerMemberLoginRuleConsultsTheMemberWideHistoryOnly() {
    givenMember();
    givenLogin(loginRule(BenefitRuleRepeatPolicy.ONCE_PER_MEMBER, null));
    when(pointEntryRepository.existsByBenefitRuleIdAndMemberId(RULE_ID, MEMBER_ID))
        .thenReturn(true);

    // 受益した翌日のログイン。日付を持たない受益歴だけを見るので、日が変わっても二件目にならない。
    serviceAt(NOON.plus(Duration.ofDays(1))).grantLoginBenefits(USER_ID);

    verify(pointEntryRepository, never()).saveAndFlush(any());
    verify(pointEntryRepository, never())
        .existsByBenefitRuleIdAndMemberIdAndGrantedOn(any(), any(), any());
  }

  @Test
  @DisplayName("日付の境界は業務時間帯の暦日で切ること（UTC ではまだ前日の時刻でも翌日の付与日になる）")
  void theGrantDateIsTheBusinessZoneCalendarDay() {
    givenMember();
    givenLogin(loginRule(BenefitRuleRepeatPolicy.ONCE_PER_DAY, null));

    // 2026-10-26 15:30 UTC = 2026-10-27 00:30 JST
    serviceAt(Instant.parse("2026-10-26T15:30:00Z")).grantLoginBenefits(USER_ID);

    assertThat(captureFlushed().getGrantedOn()).isEqualTo(LocalDate.of(2026, 10, 27));
    verify(pointEntryRepository)
        .existsByBenefitRuleIdAndMemberIdAndGrantedOn(
            RULE_ID, MEMBER_ID, LocalDate.of(2026, 10, 27));
  }

  @Test
  @DisplayName("会員身分を持たない利用者の認証では規則を引かず何も記帳しないこと")
  void aUserWithoutMembershipNeverReachesTheRules() {
    when(memberLookupService.findByUserId(USER_ID)).thenReturn(Optional.empty());

    benefitGrantService.grantLoginBenefits(USER_ID);

    verify(benefitRuleRepository, never()).findByTypeAndEnabledTrue(any());
    verify(pointEntryRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("適用期間の外にある認証の日には記帳しないこと")
  void aLoginRuleDoesNotFireOutsideItsWindow() {
    givenMember();
    givenLogin(loginRule(BenefitRuleRepeatPolicy.ONCE_PER_DAY, null));

    serviceAt(Instant.parse("2027-01-15T03:00:00Z")).grantLoginBenefits(USER_ID);

    verify(pointEntryRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("同日の並行ログインに負けて一意索引に撥ねられても、二回目として静かに終えること")
  void theRaceLoserOnTheDailyIndexEndsQuietly() {
    givenMember();
    givenLogin(loginRule(BenefitRuleRepeatPolicy.ONCE_PER_DAY, null));
    when(pointEntryRepository.saveAndFlush(any()))
        .thenThrow(violationOf(DbConstraint.UQ_T_POINT_ENTRIES_LOGIN_BENEFIT_DAY));

    benefitGrantService.grantLoginBenefits(USER_ID);
  }

  @Test
  @DisplayName("一意索引以外の失敗はそのまま投げ、呼び元（認証側）に記録させること")
  void otherFailuresPropagateToTheCaller() {
    givenMember();
    givenLogin(loginRule(BenefitRuleRepeatPolicy.ONCE_PER_DAY, null));
    when(pointEntryRepository.saveAndFlush(any()))
        .thenThrow(violationOf(DbConstraint.UQ_T_POINT_ENTRIES_IDEMPOTENCY_KEY));

    assertThatThrownBy(() -> benefitGrantService.grantLoginBenefits(USER_ID))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void givenMember() {
    when(memberLookupService.findByUserId(USER_ID))
        .thenReturn(Optional.of(new MemberLookup(MEMBER_ID, "M-0001")));
  }

  private void givenLogin(BenefitRule rule) {
    when(benefitRuleRepository.findByTypeAndEnabledTrue(BenefitRuleType.LOGIN))
        .thenReturn(List.of(rule));
  }

  private PointEntry captureFlushed() {
    ArgumentCaptor<PointEntry> captor = ArgumentCaptor.forClass(PointEntry.class);
    verify(pointEntryRepository).saveAndFlush(captor.capture());
    return captor.getValue();
  }

  private static DataIntegrityViolationException violationOf(DbConstraint constraint) {
    return new DataIntegrityViolationException(
        "違反",
        new ConstraintViolationException(
            "違反", new SQLException("違反", "23505"), constraint.sqlName()));
  }

  private static BenefitRule loginRule(BenefitRuleRepeatPolicy repeatPolicy, Integer validityDays) {
    BenefitRule rule =
        BenefitRule.define(
            BenefitRuleType.LOGIN,
            BenefitRuleDefinition.builder()
                .name("ログインボーナス")
                .storeScopeType(StoreScopeType.ALL_STORES)
                .storeIds(Set.of())
                .effectiveFrom(LocalDate.of(2026, 9, 1))
                .effectiveUntil(LocalDate.of(2026, 12, 31))
                .grantValidityDays(validityDays)
                .repeatPolicy(repeatPolicy)
                .points(100)
                .build());
    ReflectionTestUtils.setField(rule, "id", RULE_ID);
    return rule;
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
    return rule(repeatPolicy, validityDays, scopeType, 500, RULE_ID);
  }

  private static BenefitRule rule(
      BenefitRuleRepeatPolicy repeatPolicy,
      Integer validityDays,
      StoreScopeType scopeType,
      int points,
      long id) {
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
                .points(points)
                .build());
    ReflectionTestUtils.setField(rule, "id", id);
    return rule;
  }
}
