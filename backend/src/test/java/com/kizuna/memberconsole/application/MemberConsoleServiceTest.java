package com.kizuna.memberconsole.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.customer.application.CustomerMemberLinkDirectory;
import com.kizuna.customer.application.CustomerMemberLinkDirectory.LinkSegment;
import com.kizuna.customer.application.CustomerMemberLinkDirectory.MemberLinks;
import com.kizuna.customer.domain.LinkReason;
import com.kizuna.customer.domain.LinkStatus;
import com.kizuna.member.application.MemberDirectoryService;
import com.kizuna.member.application.MemberDirectoryService.MemberProfile;
import com.kizuna.member.application.MemberDirectoryService.RankTransition;
import com.kizuna.memberconsole.api.dto.MemberDetailResponse;
import com.kizuna.memberwithdrawal.application.MemberWithdrawalService;
import com.kizuna.point.application.MemberLedgerQueryService;
import com.kizuna.point.application.PointLedgerService;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.storescope.StoreScope;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberConsoleServiceTest {

  private static final OffsetDateTime AT = OffsetDateTime.parse("2026-10-26T10:00:00+09:00");
  private static final StoreScope SCOPE = new StoreScope(false, Set.of(1L));

  @Mock private MemberDirectoryService memberDirectoryService;
  @Mock private PointLedgerService pointLedgerService;
  @Mock private MemberLedgerQueryService memberLedgerQueryService;
  @Mock private CustomerMemberLinkDirectory linkDirectory;
  @Mock private MemberWithdrawalService memberWithdrawalService;

  @InjectMocks private MemberConsoleService service;

  @Test
  @DisplayName("詳細は身分・残高と期限別内訳・ランク履歴・店舗集合内の関連と集合外の件数を一つに合成する")
  void detailComposesEverySection() {
    when(memberDirectoryService.findProfile(7L)).thenReturn(Optional.of(profile(null)));
    when(pointLedgerService.balance(7L)).thenReturn(300L);
    when(pointLedgerService.balanceByExpiry(7L))
        .thenReturn(
            List.of(
                new PointLedgerService.ExpiryBalance(LocalDate.of(2026, 12, 31), 100L),
                new PointLedgerService.ExpiryBalance(null, 200L)));
    when(linkDirectory.findByMember(7L, SCOPE))
        .thenReturn(
            new MemberLinks(
                List.of(
                    new LinkSegment(
                        "L1",
                        1L,
                        "本店",
                        "C1",
                        "山田",
                        LinkStatus.ACTIVE,
                        LinkReason.MEMBER_CODE,
                        AT,
                        null,
                        null)),
                2));

    MemberDetailResponse detail = service.detail(7L, SCOPE);

    assertThat(detail.memberCode()).isEqualTo("123456789012");
    assertThat(detail.withdrawal()).isNull();
    assertThat(detail.balance()).isEqualTo(300L);
    assertThat(detail.balances())
        .extracting(
            MemberDetailResponse.ExpiryBalance::expiresOn,
            MemberDetailResponse.ExpiryBalance::points)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 12, 31), 100L),
            org.assertj.core.groups.Tuple.tuple(null, 200L));
    assertThat(detail.rankHistory())
        .extracting(MemberDetailResponse.RankTransition::newRank)
        .containsExactly("SILVER");
    assertThat(detail.links())
        .extracting(
            MemberDetailResponse.Link::storeName,
            MemberDetailResponse.Link::customerName,
            MemberDetailResponse.Link::status)
        .containsExactly(org.assertj.core.groups.Tuple.tuple("本店", "山田", "ACTIVE"));
    assertThat(detail.otherStoreLinkCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("退会済みの詳細は退会日時・理由・操作者を持つ")
  void withdrawnDetailCarriesWithdrawalFacts() {
    when(memberDirectoryService.findProfile(7L)).thenReturn(Optional.of(profile(AT)));
    when(linkDirectory.findByMember(7L, SCOPE)).thenReturn(new MemberLinks(List.of(), 0));

    MemberDetailResponse detail = service.detail(7L, SCOPE);

    assertThat(detail.withdrawal().withdrawnAt()).isEqualTo(AT);
    assertThat(detail.withdrawal().reason()).isEqualTo("電話依頼");
    assertThat(detail.withdrawal().bySelf()).isFalse();
    assertThat(detail.withdrawal().withdrawnByName()).isEqualTo("職員 太郎");
  }

  @Test
  @DisplayName("存在しない会員の詳細は 404 で、他の読み口には触れない")
  void missingMemberIsNotFound() {
    when(memberDirectoryService.findProfile(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.detail(99L, SCOPE)).isInstanceOf(NotFoundException.class);
    verifyNoInteractions(pointLedgerService, linkDirectory);
  }

  @Test
  @DisplayName("代行退会は退会の編排を会員 ID・職員の利用者 ID・理由で呼ぶ")
  void withdrawDelegatesToOrchestration() {
    service.withdraw(7L, 42L, "理由");

    verify(memberWithdrawalService).withdrawByStaff(7L, 42L, "理由");
  }

  private static MemberProfile profile(OffsetDateTime withdrawnAt) {
    return new MemberProfile(
        7L,
        "123456789012",
        10L,
        "会員 花子",
        "hanako@example.com",
        false,
        "SILVER",
        AT.minusDays(30),
        withdrawnAt,
        withdrawnAt == null ? null : "電話依頼",
        withdrawnAt == null ? null : 42L,
        false,
        withdrawnAt == null ? null : "職員 太郎",
        List.of(new RankTransition("BRONZE", "SILVER", AT.minusDays(10))));
  }
}
