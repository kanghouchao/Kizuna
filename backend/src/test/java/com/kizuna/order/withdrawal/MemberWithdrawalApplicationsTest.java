package com.kizuna.order.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.order.domain.OrderApplication;
import com.kizuna.order.domain.OrderApplicationRepository;
import com.kizuna.order.domain.OrderApplicationStatus;
import com.kizuna.shared.exception.ConflictException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;

@ExtendWith(MockitoExtension.class)
class MemberWithdrawalApplicationsTest {

  @Mock private OrderApplicationRepository applications;

  @InjectMocks private MemberWithdrawalApplications withdrawal;

  @Test
  @DisplayName("会員名義の PENDING 申請だけを引いて WITHDRAWN にし、件数を返す（CONFIRMED 以降は問い合わせの対象外）")
  void withdrawsPendingApplicationsOnly() {
    OrderApplication pending =
        OrderApplication.builder()
            .status(OrderApplicationStatus.PENDING)
            .businessDate(LocalDate.of(2026, 10, 30))
            .requesterMemberId(7L)
            .requesterMemberCode("123456789012")
            .build();
    when(applications.lockPendingByRequesterMemberIdWithoutWaiting(7L))
        .thenReturn(List.of(pending));
    OffsetDateTime at = OffsetDateTime.parse("2026-10-26T10:00:00+09:00");

    assertThat(withdrawal.withdrawPending(7L, 10L, at)).isEqualTo(1);

    assertThat(pending.getStatus()).isEqualTo(OrderApplicationStatus.WITHDRAWN);
    assertThat(pending.getProcessedBy()).isEqualTo(10L);
    assertThat(pending.getProcessedAt()).isEqualTo(at);
    verify(applications).save(pending);
    verify(applications).flush();
  }

  @Test
  @DisplayName("確定処理が押さえている申請があれば待たずに競合（409）として撥ね、何も書かないこと")
  void lockedApplicationIsReportedAsConflict() {
    when(applications.lockPendingByRequesterMemberIdWithoutWaiting(7L))
        .thenThrow(new CannotAcquireLockException("locked"));

    assertThatThrownBy(() -> withdrawal.withdrawPending(7L, 10L, OffsetDateTime.now()))
        .isInstanceOf(ConflictException.class);
    verify(applications, never()).save(any());
  }
}
