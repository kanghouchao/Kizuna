package com.kizuna.memberwithdrawal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.audit.domain.AuditEvent;
import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.customer.application.CustomerMemberLinkWithdrawal;
import com.kizuna.member.application.MemberWithdrawal;
import com.kizuna.member.application.MemberWithdrawal.WithdrawnMember;
import com.kizuna.member.domain.MemberAlreadyWithdrawnException;
import com.kizuna.order.withdrawal.MemberWithdrawalApplications;
import com.kizuna.point.application.PointLedgerService;
import com.kizuna.point.application.PointLedgerService.WithdrawalClearance;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 会員退会が、実 BusinessAudit / AuditWriter（保存先だけ差し替え）へ 1 回の退会につき 1 行を残すこと。 */
class MemberWithdrawalAuditTest {
  private static final long MEMBER_ID = 77L;
  private static final long MEMBER_USER_ID = 10L;
  private static final long STAFF_ID = 42L;
  private static final String REASON = "引っ越しのため";
  private static final OffsetDateTime AT = OffsetDateTime.parse("2026-10-26T10:00:00+09:00");

  private final MemberWithdrawal memberWithdrawal = mock(MemberWithdrawal.class);
  private final PointLedgerService ledger = mock(PointLedgerService.class);
  private final CustomerMemberLinkWithdrawal links = mock(CustomerMemberLinkWithdrawal.class);
  private final MemberWithdrawalApplications applications =
      mock(MemberWithdrawalApplications.class);
  private final List<AuditEvent> events = new ArrayList<>();
  private MemberWithdrawalService service;

  @BeforeEach
  void prepare() {
    var users = mock(UserRepository.class);
    User member = user(MEMBER_USER_ID, "会員 太郎");
    User staff = user(STAFF_ID, "職員 花子");
    when(users.findById(MEMBER_USER_ID)).thenReturn(Optional.of(member));
    when(users.findById(STAFF_ID)).thenReturn(Optional.of(staff));
    var repository = mock(AuditEventRepository.class);
    when(repository.saveAndFlush(any(AuditEvent.class)))
        .thenAnswer(
            call -> {
              AuditEvent event = call.getArgument(0);
              events.add(event);
              return event;
            });
    service =
        new MemberWithdrawalService(
            memberWithdrawal,
            ledger,
            links,
            applications,
            new BusinessAudit(users, new AuditWriter(repository, Clock.systemUTC())));
  }

  @Test
  @DisplayName("本人退会は 1 行を残す: 主体は本人、店舗なし、消去仕訳が source、件数と版だけが after に入る")
  void selfWithdrawalRecordsOneRowWithClearanceAsSource() {
    when(memberWithdrawal.withdrawSelf(MEMBER_USER_ID, REASON)).thenReturn(withdrawn());
    when(ledger.clearForWithdrawal(MEMBER_ID, MEMBER_USER_ID, "会員退会"))
        .thenReturn(new WithdrawalClearance(500, Optional.of(901L)));
    when(applications.withdrawPending(anyLong(), anyLong(), any())).thenReturn(2);

    service.withdrawSelf(MEMBER_USER_ID, REASON);

    assertThat(events).hasSize(1);
    AuditEvent event = events.getFirst();
    assertThat(event.getAction()).isEqualTo("MEMBER_WITHDRAWN");
    assertThat(event.getTargetType()).isEqualTo("MEMBER");
    assertThat(event.getTargetId()).isEqualTo("77");
    assertThat(event.getStoreId()).isNull();
    assertThat(event.getActorId()).isEqualTo(MEMBER_USER_ID);
    assertThat(event.getSourceType()).isEqualTo("POINT_LEDGER_ENTRY");
    assertThat(event.getSourceId()).isEqualTo("901");
    assertThat(event.getBeforeValues())
        .containsEntry("status", "ACTIVE")
        .containsEntry("version", "3")
        .containsEntry("user_enabled", "true");
    assertThat(event.getAfterValues())
        .containsEntry("status", "WITHDRAWN")
        .containsEntry("version", "4")
        .containsEntry("user_enabled", "false")
        .containsEntry("cleared_points", "500")
        .containsEntry("withdrawn_applications", "2")
        .containsEntry("changed_fields", "withdrawn_at,withdrawn_by,withdrawal_reason");
    assertThat(event.getAfterValues().toString()).doesNotContain(REASON);
  }

  @Test
  @DisplayName("代行退会の主体は職員で、残高 0 で仕訳が無ければ source なし・消去 0")
  void staffWithdrawalWithZeroBalanceHasNoSource() {
    when(memberWithdrawal.withdrawByStaff(MEMBER_ID, STAFF_ID, REASON)).thenReturn(withdrawn());
    when(ledger.clearForWithdrawal(MEMBER_ID, STAFF_ID, "会員退会"))
        .thenReturn(new WithdrawalClearance(0, Optional.empty()));

    service.withdrawByStaff(MEMBER_ID, STAFF_ID, REASON);

    AuditEvent event = events.getFirst();
    assertThat(event.getActorId()).isEqualTo(STAFF_ID);
    assertThat(event.getSourceType()).isNull();
    assertThat(event.getSourceId()).isNull();
    assertThat(event.getAfterValues())
        .containsEntry("cleared_points", "0")
        .containsEntry("withdrawn_applications", "0");
  }

  @Test
  @DisplayName("退会済み（409）や途中の失敗では成功監査を作らない")
  void failuresRecordNothing() {
    when(memberWithdrawal.withdrawSelf(MEMBER_USER_ID, REASON))
        .thenThrow(new MemberAlreadyWithdrawnException());
    assertThatThrownBy(() -> service.withdrawSelf(MEMBER_USER_ID, REASON))
        .isInstanceOf(MemberAlreadyWithdrawnException.class);

    when(memberWithdrawal.withdrawByStaff(MEMBER_ID, STAFF_ID, REASON)).thenReturn(withdrawn());
    when(ledger.clearForWithdrawal(MEMBER_ID, STAFF_ID, "会員退会"))
        .thenReturn(new WithdrawalClearance(0, Optional.empty()));
    doThrow(new IllegalStateException("関連の解除に失敗")).when(links).releaseAll(MEMBER_ID, STAFF_ID, AT);
    assertThatThrownBy(() -> service.withdrawByStaff(MEMBER_ID, STAFF_ID, REASON))
        .isInstanceOf(IllegalStateException.class);

    assertThat(events).isEmpty();
  }

  private static WithdrawnMember withdrawn() {
    return new WithdrawnMember(MEMBER_ID, MEMBER_USER_ID, AT, 3L, 4L, true);
  }

  private static User user(long id, String name) {
    User user =
        User.builder()
            .email(id + "@kizuna.test")
            .password("encoded")
            .displayName(name)
            .enabled(true)
            .userType(id == STAFF_ID ? UserType.STAFF : UserType.MEMBER)
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(id == STAFF_ID ? Set.of(1L) : Set.of())
            .roleIds(id == STAFF_ID ? Set.of(1L) : Set.of())
            .build();
    user.setId(id);
    return user;
  }
}
