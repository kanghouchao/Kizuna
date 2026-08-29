package com.kizuna.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.member.application.MemberRankService;
import com.kizuna.order.domain.OrderAttributionRepository;
import com.kizuna.order.domain.OrderAttributionStatus;
import com.kizuna.point.application.PointLedgerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberRankSyncTest {

  private static final long MEMBER_ID = 7L;
  private static final long ENTRY_ID = 41L;

  @Mock private OrderAttributionRepository orderAttributionRepository;
  @Mock private PointLedgerService pointLedgerService;
  @Mock private MemberRankService memberRankService;

  @InjectMocks private MemberRankSync memberRankSync;

  @Test
  @DisplayName("判定へは値ではなく供給口を渡すこと（読む時点は会員行のロックの内側で決まる）")
  void handsTheMetricsSupplierToTheRankServiceInsteadOfValues() {
    memberRankSync.afterGrant(MEMBER_ID, ENTRY_ID);

    verify(memberRankService).syncOnGrant(MEMBER_ID, memberRankSync, ENTRY_ID);
    // 渡した時点では材料をまだ読んでいない
    verifyNoInteractions(orderAttributionRepository);
    verifyNoInteractions(pointLedgerService);
  }

  @Test
  @DisplayName("来店回数は有効な帰属だけを数えること（無効化された帰属は入らない）")
  void countsOnlyActiveAttributions() {
    when(orderAttributionRepository.countByMemberIdAndStatus(
            MEMBER_ID, OrderAttributionStatus.ACTIVE))
        .thenReturn(6L);

    assertThat(memberRankSync.completedVisitCount(MEMBER_ID)).isEqualTo(6L);
  }

  @Test
  @DisplayName("付与の指標は台帳の純額（取消仕訳の控除後）であること")
  void readsTheNetGrantedPointsFromTheLedger() {
    when(pointLedgerService.netGrantedPointsFor(MEMBER_ID)).thenReturn(4200L);

    assertThat(memberRankSync.netGrantedPoints(MEMBER_ID)).isEqualTo(4200L);
  }

  @Test
  @DisplayName("付与が記帳されていない回は判定そのものを起こさないこと")
  void skipsEvaluationWhenNothingWasBooked() {
    memberRankSync.afterGrant(MEMBER_ID, null);

    verify(memberRankService, never()).syncOnGrant(anyLong(), any(), anyLong());
    verifyNoInteractions(orderAttributionRepository);
    verifyNoInteractions(pointLedgerService);
  }
}
