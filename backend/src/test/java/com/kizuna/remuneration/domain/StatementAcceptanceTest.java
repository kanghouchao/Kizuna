package com.kizuna.remuneration.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.remuneration.domain.DailyGuarantee.Status;
import com.kizuna.remuneration.domain.StatementContent.BonusBasis;
import com.kizuna.remuneration.domain.StatementContent.OrderBasis;
import com.kizuna.shared.exception.ServiceException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatementAcceptanceTest {
  private static final LocalDate DAY = LocalDate.of(2026, 10, 1);
  private static final OffsetDateTime AT =
      OffsetDateTime.of(2026, 10, 2, 9, 0, 0, 0, ZoneOffset.ofHours(9));

  private static StatementContent content(
      List<OrderBasis> orders, Status status, Long guarantee, List<BonusBasis> bonuses) {
    long order = orders.stream().mapToLong(OrderBasis::effectiveAmount).sum();
    long bonus = bonuses.stream().mapToLong(BonusBasis::amount).sum();
    return new StatementContent(
        order, guarantee, bonus, orders, "PT5H", status, GuaranteeState.ACTIVE, 10000L, bonuses);
  }

  private static StatementContent calculated(long orderAmount) {
    return content(
        List.of(new OrderBasis("o1", orderAmount, false)),
        Status.CALCULATED,
        Math.max(0, 10000 - orderAmount),
        List.of());
  }

  private static StatementAcceptance accepted(StatementContent content, int minutes) {
    return StatementAcceptance.accept(1L, 7L, DAY, content, AT.plusMinutes(minutes));
  }

  @Test
  void canonicalFormListsAmountsAndEvidenceInAFixedOrder() {
    var content =
        content(
            List.of(new OrderBasis("o2", 3000, false), new OrderBasis("o1", 4000, true)),
            Status.CALCULATED,
            7000L,
            List.of(new BonusBasis("b9", 500), new BonusBasis("b1", 200)));
    assertThat(content.canonical())
        .isEqualTo(
            """
            v1
            order_amount=3000
            guarantee_amount=7000
            bonus_amount=700
            closed_duration=PT5H
            guarantee_status=CALCULATED
            guarantee_state=ACTIVE
            daily_amount=10000
            order=o1,4000,invalidated
            order=o2,3000,valid
            bonus=b1,200
            bonus=b9,500
            """);
    assertThat(content.digest()).hasSize(64).matches("[0-9a-f]+");
  }

  @Test
  void digestIgnoresInputOrderButSeesEveryPieceOfEvidence() {
    var a = new OrderBasis("o1", 3000, false);
    var b = new OrderBasis("o2", 2000, false);
    var base = content(List.of(a, b), Status.CALCULATED, 5000L, List.of(new BonusBasis("b1", 1)));
    assertThat(
            content(List.of(b, a), Status.CALCULATED, 5000L, List.of(new BonusBasis("b1", 1)))
                .digest())
        .isEqualTo(base.digest());
    // 額が同じでも根拠（どの受注・どのボーナスか）が違えば別の内容。
    assertThat(
            content(
                    List.of(new OrderBasis("o3", 3000, false), b),
                    Status.CALCULATED,
                    5000L,
                    List.of(new BonusBasis("b1", 1)))
                .digest())
        .isNotEqualTo(base.digest());
    assertThat(
            content(List.of(a, b), Status.CALCULATED, 5000L, List.of(new BonusBasis("b2", 1)))
                .digest())
        .isNotEqualTo(base.digest());
    // 保証額 0 のまま日額の条件だけ変わっても別の内容。
    var rich = calculated(20000);
    var raised =
        new StatementContent(
            20000,
            0L,
            0,
            rich.orders(),
            "PT5H",
            Status.CALCULATED,
            GuaranteeState.ACTIVE,
            15000L,
            List.of());
    assertThat(raised.digest()).isNotEqualTo(rich.digest());
  }

  @Test
  void onlyAnEndedDayWithADeterminedGuaranteeAndANonZeroAmountIsAcceptable() {
    assertThat(calculated(3000).notAcceptableReason(false))
        .isEqualTo(NotAcceptableReason.BUSINESS_DAY_NOT_ENDED);
    assertThat(
            content(List.of(), Status.PENDING_ATTENDANCE, null, List.of(new BonusBasis("b", 1)))
                .notAcceptableReason(true))
        .isEqualTo(NotAcceptableReason.GUARANTEE_UNDETERMINED);
    assertThat(
            content(List.of(), Status.NOT_CONFIGURED, null, List.of(new BonusBasis("b", 1)))
                .notAcceptableReason(true))
        .isEqualTo(NotAcceptableReason.GUARANTEE_UNDETERMINED);
    assertThat(content(List.of(), Status.NOT_ELIGIBLE, 0L, List.of()).notAcceptableReason(true))
        .isEqualTo(NotAcceptableReason.ALL_ZERO);
    assertThat(
            content(List.of(), Status.STOPPED, 0L, List.of(new BonusBasis("b", 1)))
                .notAcceptableReason(true))
        .isNull();
    assertThat(
            content(List.of(), Status.NOT_ELIGIBLE, 0L, List.of(new BonusBasis("b", 1)))
                .notAcceptableReason(true))
        .isNull();
    assertThat(calculated(3000).notAcceptableReason(true)).isNull();
  }

  @Test
  void acceptingAnUndeterminedContentIsRefused() {
    var pending =
        content(List.of(), Status.PENDING_ATTENDANCE, null, List.of(new BonusBasis("b", 1)));
    assertThatThrownBy(() -> StatementAcceptance.accept(1L, 7L, DAY, pending, AT))
        .isInstanceOf(ServiceException.class);
  }

  @Test
  void stateWithoutAcceptanceIsNotAccepted() {
    var day = DayAcceptance.resolve(calculated(3000), true, List.of());
    assertThat(day.state()).isEqualTo(AcceptanceState.NOT_ACCEPTED);
    assertThat(day.contentDigest()).isEqualTo(calculated(3000).digest());
    assertThat(day.latest()).isNull();
  }

  @Test
  void correctionAfterAcceptanceShowsChangedAgainstTheLatestAcceptance() {
    var first = accepted(calculated(3000), 0);
    var day = DayAcceptance.resolve(calculated(4000), true, List.of(first));
    assertThat(day.state()).isEqualTo(AcceptanceState.CHANGED_AFTER_ACCEPTANCE);
    assertThat(day.latest()).isSameAs(first);
    assertThat(DayAcceptance.resolve(calculated(3000), true, List.of(first)).state())
        .isEqualTo(AcceptanceState.ACCEPTED);
  }

  @Test
  void contentThatReturnsToAnAcceptedVersionIsAcceptedAgain() {
    var a = accepted(calculated(3000), 0);
    var b = accepted(calculated(4000), 5);
    var day = DayAcceptance.resolve(calculated(3000), true, List.of(b, a));
    assertThat(day.state()).isEqualTo(AcceptanceState.ACCEPTED);
    assertThat(day.latest()).isSameAs(b);
  }

  @Test
  void notAcceptableWinsAndStillCarriesThePastAcceptance() {
    var a = accepted(calculated(3000), 0);
    var pending =
        content(
            List.of(new OrderBasis("o1", 3000, false)), Status.PENDING_ATTENDANCE, null, List.of());
    var day = DayAcceptance.resolve(pending, true, List.of(a));
    assertThat(day.state()).isEqualTo(AcceptanceState.NOT_ACCEPTABLE);
    assertThat(day.reason()).isEqualTo(NotAcceptableReason.GUARANTEE_UNDETERMINED);
    assertThat(day.contentDigest()).isNull();
    assertThat(day.latest()).isSameAs(a);
  }

  @Test
  void acceptanceCopiesTheContentAndItsDigest() {
    var content = calculated(3000);
    var row = StatementAcceptance.accept(1L, 7L, DAY, content, AT);
    assertThat(row.getStoreId()).isEqualTo(1L);
    assertThat(row.getPersonId()).isEqualTo(7L);
    assertThat(row.getBusinessDate()).isEqualTo(DAY);
    assertThat(row.getAcceptedAt()).isEqualTo(AT);
    assertThat(row.getContentDigest()).isEqualTo(content.digest());
    assertThat(row.getContent()).isEqualTo(content.canonical());
    assertThat(row.getOrderAmount()).isEqualTo(3000);
    assertThat(row.getGuaranteeAmount()).isEqualTo(7000);
    assertThat(row.getBonusAmount()).isZero();
  }
}
