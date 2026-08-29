package com.kizuna.order.application;

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
  @DisplayName("有効な帰属の件数と付与の純額を材料として判定へ渡すこと")
  void passesTheCrossStoreMetricsToTheRankService() {
    when(orderAttributionRepository.countByMemberIdAndStatus(
            MEMBER_ID, OrderAttributionStatus.ACTIVE))
        .thenReturn(6L);
    when(pointLedgerService.netGrantedPointsFor(MEMBER_ID)).thenReturn(4200L);

    memberRankSync.afterGrant(MEMBER_ID, ENTRY_ID);

    verify(memberRankService).syncOnGrant(MEMBER_ID, 6L, 4200L, ENTRY_ID);
  }

  @Test
  @DisplayName("付与が記帳されていない回は判定そのものを起こさないこと")
  void skipsEvaluationWhenNothingWasBooked() {
    memberRankSync.afterGrant(MEMBER_ID, null);

    verify(memberRankService, never()).syncOnGrant(anyLong(), anyLong(), anyLong(), anyLong());
    verifyNoInteractions(orderAttributionRepository);
    verifyNoInteractions(pointLedgerService);
  }
}
