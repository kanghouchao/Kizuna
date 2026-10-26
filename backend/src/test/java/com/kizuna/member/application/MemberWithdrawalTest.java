package com.kizuna.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.member.domain.Member;
import com.kizuna.member.domain.MemberAlreadyWithdrawnException;
import com.kizuna.member.domain.MemberRepository;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.user.application.CredentialOperations;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberWithdrawalTest {

  @Mock private UserRepository userRepository;
  @Mock private MemberRepository memberRepository;
  @Mock private CredentialOperations credentialOperations;

  @InjectMocks private MemberWithdrawal withdrawal;

  private static User memberUser() {
    User user =
        User.builder()
            .email("member@example.com")
            .password("encoded")
            .displayName("会員 花子")
            .enabled(true)
            .userType(UserType.MEMBER)
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of())
            .build();
    user.setId(10L);
    return user;
  }

  private static Member member() {
    Member member = Member.builder().memberCode("123456789012").userId(10L).build();
    member.setId(77L);
    return member;
  }

  @Test
  @DisplayName("本人退会は会員行をロック付きで引き、理由を記録し、利用者を停止・退会済みにする")
  void withdrawSelfLocksMemberAndStopsUser() {
    User user = memberUser();
    Member member = member();
    when(userRepository.findById(10L)).thenReturn(Optional.of(user));
    when(memberRepository.findByUserIdForUpdate(10L)).thenReturn(Optional.of(member));

    var withdrawn = withdrawal.withdrawSelf(10L, "引っ越し");

    assertThat(withdrawn.memberId()).isEqualTo(77L);
    assertThat(withdrawn.userId()).isEqualTo(10L);
    assertThat(member.isWithdrawn()).isTrue();
    assertThat(member.getWithdrawalReason()).isEqualTo("引っ越し");
    assertThat(member.getWithdrawnBy()).isEqualTo(10L);
    assertThat(member.getWithdrawnAt()).isEqualTo(withdrawn.withdrawnAt());
    verify(credentialOperations).withdraw(user, withdrawn.withdrawnAt());
    verify(memberRepository).save(member);
    verify(userRepository).save(user);
  }

  @Test
  @DisplayName("退会済みの再退会は 409 で、利用者には触れない")
  void secondWithdrawalConflicts() {
    Member member = member();
    member.withdraw("一度目", 10L, OffsetDateTime.now());
    when(userRepository.findById(10L)).thenReturn(Optional.of(memberUser()));
    when(memberRepository.findByUserIdForUpdate(10L)).thenReturn(Optional.of(member));

    assertThatThrownBy(() -> withdrawal.withdrawSelf(10L, "二度目"))
        .isInstanceOf(MemberAlreadyWithdrawnException.class);
    verify(credentialOperations, never()).withdraw(any(), any());
  }

  @Test
  @DisplayName("主体や会員が無い（退会済みの旧トークン等）はセッション無効として 401")
  void missingPrincipalOrMemberIsStaleSession() {
    when(userRepository.findById(99L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> withdrawal.withdrawSelf(99L, "理由"))
        .isInstanceOf(StaleSessionException.class);

    when(userRepository.findById(10L)).thenReturn(Optional.of(memberUser()));
    when(memberRepository.findByUserIdForUpdate(10L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> withdrawal.withdrawSelf(10L, "理由"))
        .isInstanceOf(StaleSessionException.class);
  }

  @Test
  @DisplayName("代行退会は会員 ID でロック付きに引き、操作者を職員の利用者 ID で記録し、本人と同じく利用者を停止する")
  void withdrawByStaffLocksMemberByIdAndRecordsStaffActor() {
    User user = memberUser();
    Member member = member();
    when(memberRepository.findByIdForUpdate(77L)).thenReturn(Optional.of(member));
    when(userRepository.findById(10L)).thenReturn(Optional.of(user));

    var withdrawn = withdrawal.withdrawByStaff(77L, 42L, "電話依頼");

    assertThat(withdrawn.memberId()).isEqualTo(77L);
    assertThat(withdrawn.userId()).isEqualTo(10L);
    assertThat(member.getWithdrawnBy()).isEqualTo(42L);
    assertThat(member.getWithdrawalReason()).isEqualTo("電話依頼");
    verify(credentialOperations).withdraw(user, withdrawn.withdrawnAt());
    verify(memberRepository).save(member);
    verify(userRepository).save(user);
  }

  @Test
  @DisplayName("代行退会の対象が無ければ 404、退会済みなら 409 で、利用者には触れない")
  void withdrawByStaffMissingOrAlreadyWithdrawn() {
    when(memberRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> withdrawal.withdrawByStaff(99L, 42L, "理由"))
        .isInstanceOf(NotFoundException.class);

    Member member = member();
    member.withdraw("一度目", 10L, OffsetDateTime.now());
    when(memberRepository.findByIdForUpdate(77L)).thenReturn(Optional.of(member));
    when(userRepository.findById(10L)).thenReturn(Optional.of(memberUser()));
    assertThatThrownBy(() -> withdrawal.withdrawByStaff(77L, 42L, "二度目"))
        .isInstanceOf(MemberAlreadyWithdrawnException.class);
    verify(credentialOperations, never()).withdraw(any(), any());
  }
}
