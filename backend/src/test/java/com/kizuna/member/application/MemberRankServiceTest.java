package com.kizuna.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.member.domain.Member;
import com.kizuna.member.domain.MemberRank;
import com.kizuna.member.domain.MemberRankHistory;
import com.kizuna.member.domain.MemberRankHistoryRepository;
import com.kizuna.member.domain.MemberRepository;
import com.kizuna.settings.application.MemberRankSettings;
import com.kizuna.settings.application.MemberRankSettings.Threshold;
import com.kizuna.settings.application.SystemConfigService;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberRankServiceTest {

  private static final long MEMBER_ID = 7L;
  private static final long ENTRY_ID = 41L;

  /** 種子既定値と同じ形。SILVER は 5 回 or 5,000pt、GOLD は 20 回 or 20,000pt。 */
  private static final MemberRankSettings SEEDED =
      new MemberRankSettings(new Threshold(5, 5000), new Threshold(20, 20000));

  @Mock private MemberRepository memberRepository;
  @Mock private MemberRankHistoryRepository memberRankHistoryRepository;
  @Mock private SystemConfigService systemConfigService;

  @InjectMocks private MemberRankService memberRankService;

  @Captor private ArgumentCaptor<MemberRankHistory> savedHistory;

  @Test
  @DisplayName("回数だけが閾値に達しても昇格すること（条件は OR）")
  void promotesOnVisitCountAlone() {
    Member member = stubMember();
    when(systemConfigService.memberRankSettings()).thenReturn(SEEDED);

    memberRankService.syncOnGrant(MEMBER_ID, 5, 0, ENTRY_ID);

    assertThat(member.getRank()).isEqualTo(MemberRank.SILVER);
  }

  @Test
  @DisplayName("付与の純額だけが閾値に達しても昇格すること（条件は OR）")
  void promotesOnGrantedPointsAlone() {
    Member member = stubMember();
    when(systemConfigService.memberRankSettings()).thenReturn(SEEDED);

    memberRankService.syncOnGrant(MEMBER_ID, 1, 5000, ENTRY_ID);

    assertThat(member.getRank()).isEqualTo(MemberRank.SILVER);
  }

  @Test
  @DisplayName("どちらの条件にも届かなければ昇格せず、履歴も残らないこと")
  void staysWhenNeitherConditionIsMet() {
    Member member = stubMember();
    when(systemConfigService.memberRankSettings()).thenReturn(SEEDED);

    memberRankService.syncOnGrant(MEMBER_ID, 4, 4999, ENTRY_ID);

    assertThat(member.getRank()).isEqualTo(MemberRank.BRONZE);
    verify(memberRankHistoryRepository, never()).save(any());
  }

  @Test
  @DisplayName("上位の条件まで届いていれば途中の等級を飛ばして最上位へ上がること")
  void promotesToTheHighestReachedRank() {
    Member member = stubMember();
    when(systemConfigService.memberRankSettings()).thenReturn(SEEDED);

    memberRankService.syncOnGrant(MEMBER_ID, 20, 0, ENTRY_ID);

    assertThat(member.getRank()).isEqualTo(MemberRank.GOLD);
  }

  @Test
  @DisplayName("指標が閾値を割っても降格しないこと（棘輪）")
  void neverDemotesWhenTheMetricFallsBack() {
    Member member = stubMember();
    member.promoteTo(MemberRank.GOLD);
    when(systemConfigService.memberRankSettings()).thenReturn(SEEDED);

    memberRankService.syncOnGrant(MEMBER_ID, 0, 0, ENTRY_ID);

    assertThat(member.getRank()).isEqualTo(MemberRank.GOLD);
    verify(memberRankHistoryRepository, never()).save(any());
  }

  @Test
  @DisplayName("閾値が未設定（0 以下）なら、その条件は成立しえないこと")
  void zeroThresholdIsNeverReached() {
    Member member = stubMember();
    when(systemConfigService.memberRankSettings())
        .thenReturn(new MemberRankSettings(new Threshold(0, 0), new Threshold(0, 0)));

    memberRankService.syncOnGrant(MEMBER_ID, 999, 999_999, ENTRY_ID);

    assertThat(member.getRank()).isEqualTo(MemberRank.BRONZE);
  }

  @Test
  @DisplayName("閾値は判定のたびに読み直され、変更が次回の判定へ反映されること")
  void rereadsThresholdsOnEveryEvaluation() {
    Member member = stubMember();
    when(systemConfigService.memberRankSettings())
        .thenReturn(new MemberRankSettings(new Threshold(10, 0), new Threshold(0, 0)))
        .thenReturn(new MemberRankSettings(new Threshold(3, 0), new Threshold(0, 0)));

    memberRankService.syncOnGrant(MEMBER_ID, 5, 0, ENTRY_ID);
    assertThat(member.getRank()).isEqualTo(MemberRank.BRONZE);

    memberRankService.syncOnGrant(MEMBER_ID, 5, 0, ENTRY_ID);
    assertThat(member.getRank()).isEqualTo(MemberRank.SILVER);
  }

  @Test
  @DisplayName("昇格のたびに、会員・時刻・遷移前後・根拠を持つ履歴行が残ること")
  void recordsAHistoryRowPerPromotion() {
    stubMember();
    when(systemConfigService.memberRankSettings()).thenReturn(SEEDED);

    memberRankService.syncOnGrant(MEMBER_ID, 5, 0, ENTRY_ID);

    verify(memberRankHistoryRepository).save(savedHistory.capture());
    MemberRankHistory history = savedHistory.getValue();
    assertThat(history.getMemberId()).isEqualTo(MEMBER_ID);
    assertThat(history.getPreviousRank()).isEqualTo(MemberRank.BRONZE);
    assertThat(history.getNewRank()).isEqualTo(MemberRank.SILVER);
    assertThat(history.getTriggeringEntryId()).isEqualTo(ENTRY_ID);
    assertThat(history.getPromotedAt()).isNotNull();
  }

  /** 判定は「読んで、上位なら書く」なので、会員行は書き込み用のロック付きで引かれる。 */
  private Member stubMember() {
    Member member = Member.builder().memberCode("123456789012").platformUserId(1L).build();
    when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));
    return member;
  }
}
