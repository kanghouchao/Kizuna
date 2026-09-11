package com.kizuna.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.member.application.MemberRankService;
import com.kizuna.order.domain.OrderAttribution;
import com.kizuna.order.domain.OrderAttributionRepository;
import com.kizuna.order.domain.OrderAttributionSource;
import com.kizuna.order.domain.OrderAttributionStatus;
import com.kizuna.point.application.BenefitGrantService;
import com.kizuna.point.application.PointLedgerService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttributionMaterializerTest {
  private static final LocalDate BUSINESS_DATE = LocalDate.parse("2026-08-10");
  private static final OffsetDateTime OCCURRED_AT =
      OffsetDateTime.parse("2026-09-01T12:00:00+09:00");
  @Mock private OrderAttributionRepository attributions;
  @Mock private PointLedgerService ledger;
  @Mock private BenefitGrantService benefits;
  @Mock private MemberRankService ranks;
  @InjectMocks private AttributionMaterializer materializer;

  @Test
  @DisplayName("完了は会員ロック・利用・帰属・付与・特典・昇格の順で成立すること")
  void materializesCompletionInContractOrder() {
    when(attributions.save(any()))
        .thenAnswer(
            invocation -> {
              OrderAttribution attribution = invocation.getArgument(0);
              attribution.setId(88L);
              return attribution;
            });
    when(ledger.grantForOrder(7L, "o1", 3L, 12000, 10L))
        .thenReturn(new PointLedgerService.GrantedPoints(120, 41L));
    when(benefits.grantVisitBenefits(7L, "o1", 3L, BUSINESS_DATE, 10L)).thenReturn(500L);
    var result =
        materializer.materialize(
            7L,
            "123456789012",
            "o1",
            3L,
            BUSINESS_DATE,
            OCCURRED_AT,
            10L,
            new AttributionMaterializer.Completion(12000, 300));
    assertThat(result).isEqualTo(new AttributionMaterializer.Result(120, 500L));
    var order = inOrder(ranks, ledger, attributions, benefits);
    order.verify(ranks).lockForPromotion(7L);
    order.verify(ledger).useForOrder(7L, "o1", 3L, 300, 10L);
    var saved = ArgumentCaptor.forClass(OrderAttribution.class);
    order.verify(attributions).save(saved.capture());
    order.verify(ledger).grantForOrder(7L, "o1", 3L, 12000, 10L);
    order.verify(benefits).grantVisitBenefits(7L, "o1", 3L, BUSINESS_DATE, 10L);
    order.verify(ranks).syncOnAttribution(7L, materializer, 88L, 41L);
    order.verifyNoMoreInteractions();
    assertThat(saved.getValue().getSource()).isEqualTo(OrderAttributionSource.COMPLETION);
    assertThat(saved.getValue().getMemberId()).isEqualTo(7L);
    assertThat(saved.getValue().getMemberCode()).isEqualTo("123456789012");
    assertThat(saved.getValue().getOrderId()).isEqualTo("o1");
    assertThat(saved.getValue().getAttributedAt()).isEqualTo(OCCURRED_AT);
  }

  @Test
  @DisplayName("申領は固定額で帰属・付与・特典・昇格を行い、ポイントを利用しないこと")
  void materializesReceiptWithTheFixedGrant() {
    when(attributions.save(any()))
        .thenAnswer(
            invocation -> {
              OrderAttribution attribution = invocation.getArgument(0);
              attribution.setId(88L);
              return attribution;
            });
    when(ledger.grantPlannedForOrder(7L, "o1", 3L, 90, 10L)).thenReturn(41L);
    when(benefits.grantVisitBenefits(7L, "o1", 3L, BUSINESS_DATE, 10L)).thenReturn(500L);
    var result =
        materializer.materialize(
            7L,
            "123456789012",
            "o1",
            3L,
            BUSINESS_DATE,
            OCCURRED_AT,
            10L,
            new AttributionMaterializer.ReceiptClaim(90));
    assertThat(result).isEqualTo(new AttributionMaterializer.Result(90, 500L));
    var order = inOrder(ranks, ledger, attributions, benefits);
    order.verify(ranks).lockForPromotion(7L);
    var saved = ArgumentCaptor.forClass(OrderAttribution.class);
    order.verify(attributions).save(saved.capture());
    order.verify(ledger).grantPlannedForOrder(7L, "o1", 3L, 90, 10L);
    order.verify(benefits).grantVisitBenefits(7L, "o1", 3L, BUSINESS_DATE, 10L);
    order.verify(ranks).syncOnAttribution(7L, materializer, 88L, 41L);
    order.verifyNoMoreInteractions();
    assertThat(saved.getValue().getSource()).isEqualTo(OrderAttributionSource.RECEIPT_TOKEN);
    assertThat(saved.getValue().getAttributedAt()).isEqualTo(OCCURRED_AT);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  @DisplayName("付与がゼロでも来店と特典が成立し、仕訳なしで昇格を判定すること")
  void evaluatesZeroPointVisitsForBothTriggers(boolean completion) {
    when(attributions.save(any()))
        .thenAnswer(
            invocation -> {
              OrderAttribution attribution = invocation.getArgument(0);
              attribution.setId(88L);
              return attribution;
            });
    AttributionMaterializer.Trigger trigger;
    if (completion) {
      trigger = new AttributionMaterializer.Completion(0, 0);
      when(ledger.grantForOrder(7L, "o1", 3L, 0, 10L))
          .thenReturn(new PointLedgerService.GrantedPoints(0, null));
    } else {
      trigger = new AttributionMaterializer.ReceiptClaim(0);
      when(ledger.grantPlannedForOrder(7L, "o1", 3L, 0, 10L)).thenReturn(null);
    }
    when(benefits.grantVisitBenefits(7L, "o1", 3L, BUSINESS_DATE, 10L)).thenReturn(500L);
    assertThat(
            materializer.materialize(
                7L, "123456789012", "o1", 3L, BUSINESS_DATE, OCCURRED_AT, 10L, trigger))
        .isEqualTo(new AttributionMaterializer.Result(0, 500));
    verify(attributions).save(any());
    verify(ranks).syncOnAttribution(7L, materializer, 88L, null);
    verify(ledger, never()).useForOrder(anyLong(), any(), any(), anyInt(), any());
  }

  @Test
  @DisplayName("来店回数は有効な帰属だけを数えること")
  void countsOnlyActiveAttributions() {
    when(attributions.countByMemberIdAndStatus(7L, OrderAttributionStatus.ACTIVE)).thenReturn(6L);
    assertThat(materializer.completedVisitCount(7L)).isEqualTo(6L);
  }

  @Test
  @DisplayName("昇格の金額指標は特典を含まない受注付与の純額であること")
  void readsTheNetOrderGrantsFromTheLedger() {
    when(ledger.netGrantedPointsFor(7L)).thenReturn(4200L);
    assertThat(materializer.netGrantedPoints(7L)).isEqualTo(4200L);
  }
}
