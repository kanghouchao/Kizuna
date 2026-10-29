package com.kizuna.memberwithdrawal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.customer.application.CustomerMemberLinkWithdrawal;
import com.kizuna.member.application.MemberWithdrawal;
import com.kizuna.member.application.MemberWithdrawal.WithdrawnMember;
import com.kizuna.order.withdrawal.MemberWithdrawalApplications;
import com.kizuna.point.application.PointLedgerService;
import com.kizuna.point.application.PointLedgerService.WithdrawalClearance;
import com.kizuna.user.application.BusinessAudit;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class MemberWithdrawalServiceTest {

  private static final OffsetDateTime AT = OffsetDateTime.parse("2026-10-26T10:00:00+09:00");

  @Mock private MemberWithdrawal memberWithdrawal;
  @Mock private PointLedgerService pointLedgerService;
  @Mock private CustomerMemberLinkWithdrawal linkWithdrawal;
  @Mock private MemberWithdrawalApplications applicationWithdrawal;
  @Mock private BusinessAudit audit;

  @InjectMocks private MemberWithdrawalService service;

  @BeforeEach
  void clearanceStub() {
    lenient()
        .when(pointLedgerService.clearForWithdrawal(anyLong(), anyLong(), any()))
        .thenReturn(new WithdrawalClearance(0, Optional.empty()));
  }

  @Test
  @DisplayName("会員身分の終端化 → ポイント退会消去 → 関連解除 → 申請取り下げ の順で、同じ会員・操作者・日時を渡す")
  void orchestratesInContractOrder() {
    when(memberWithdrawal.withdrawSelf(10L, "理由"))
        .thenReturn(new WithdrawnMember(77L, 10L, AT, 1L, 2L, true));

    service.withdrawSelf(10L, "理由");

    var order =
        inOrder(memberWithdrawal, pointLedgerService, linkWithdrawal, applicationWithdrawal);
    order.verify(memberWithdrawal).withdrawSelf(10L, "理由");
    order.verify(pointLedgerService).clearForWithdrawal(77L, 10L, "会員退会");
    order.verify(linkWithdrawal).releaseAll(77L, 10L, AT);
    order.verify(applicationWithdrawal).withdrawPending(77L, 10L, AT);
  }

  @Test
  @DisplayName("途中の失敗は握らず伝播させ、以後の段へ進まない（巻き戻しは取引境界が担う）")
  void failurePropagatesAndStopsLaterSteps() throws Exception {
    when(memberWithdrawal.withdrawSelf(10L, "理由"))
        .thenReturn(new WithdrawnMember(77L, 10L, AT, 1L, 2L, true));
    doThrow(new IllegalStateException("関連の解除に失敗")).when(linkWithdrawal).releaseAll(77L, 10L, AT);

    assertThatThrownBy(() -> service.withdrawSelf(10L, "理由"))
        .isInstanceOf(IllegalStateException.class);

    verify(applicationWithdrawal, never()).withdrawPending(anyLong(), anyLong(), any());
    assertThat(
            MemberWithdrawalService.class
                .getMethod("withdrawSelf", long.class, String.class)
                .isAnnotationPresent(Transactional.class))
        .as("全段が一つの取引で確定・巻き戻しされる")
        .isTrue();
  }

  @Test
  @DisplayName("代行退会は会員 ID で終端化し、消去仕訳・関連解除・申請取り下げの操作者を職員の利用者 ID にする")
  void staffWithdrawalRecordsStaffAsActorEverywhere() {
    when(memberWithdrawal.withdrawByStaff(77L, 42L, "電話依頼"))
        .thenReturn(new WithdrawnMember(77L, 10L, AT, 1L, 2L, true));

    service.withdrawByStaff(77L, 42L, "電話依頼");

    var order =
        inOrder(memberWithdrawal, pointLedgerService, linkWithdrawal, applicationWithdrawal);
    order.verify(memberWithdrawal).withdrawByStaff(77L, 42L, "電話依頼");
    order.verify(pointLedgerService).clearForWithdrawal(77L, 42L, "会員退会");
    order.verify(linkWithdrawal).releaseAll(77L, 42L, AT);
    order.verify(applicationWithdrawal).withdrawPending(77L, 42L, AT);
  }
}
