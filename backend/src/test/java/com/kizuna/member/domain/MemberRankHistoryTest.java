package com.kizuna.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberRankHistoryTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-29T12:00:00+09:00");

  @Test
  @DisplayName("会員・遷移前後・根拠・時刻を持って構築できる")
  void buildsWithTheTransitionAndItsTrigger() {
    MemberRankHistory history =
        MemberRankHistory.promoted(7L, MemberRank.BRONZE, MemberRank.SILVER, 41L, NOW);

    assertThat(history.getMemberId()).isEqualTo(7L);
    assertThat(history.getPreviousRank()).isEqualTo(MemberRank.BRONZE);
    assertThat(history.getNewRank()).isEqualTo(MemberRank.SILVER);
    assertThat(history.getTriggeringEntryId()).isEqualTo(41L);
    assertThat(history.getPromotedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("下位・同格への遷移は記録できない（降格は存在しない）")
  void rejectsAnythingButAnUpgrade() {
    assertThatThrownBy(
            () -> MemberRankHistory.promoted(7L, MemberRank.GOLD, MemberRank.SILVER, 41L, NOW))
        .isInstanceOf(InvalidMemberException.class);
    assertThatThrownBy(
            () -> MemberRankHistory.promoted(7L, MemberRank.SILVER, MemberRank.SILVER, 41L, NOW))
        .isInstanceOf(InvalidMemberException.class);
    assertThatThrownBy(() -> MemberRankHistory.promoted(7L, null, MemberRank.SILVER, 41L, NOW))
        .isInstanceOf(InvalidMemberException.class);
  }

  @Test
  @DisplayName("会員・根拠・時刻の欠落は記録できない")
  void rejectsMissingIdentifiers() {
    assertThatThrownBy(
            () -> MemberRankHistory.promoted(null, MemberRank.BRONZE, MemberRank.SILVER, 41L, NOW))
        .isInstanceOf(InvalidMemberException.class);
    assertThatThrownBy(
            () -> MemberRankHistory.promoted(7L, MemberRank.BRONZE, MemberRank.SILVER, null, NOW))
        .isInstanceOf(InvalidMemberException.class);
    assertThatThrownBy(
            () -> MemberRankHistory.promoted(7L, MemberRank.BRONZE, MemberRank.SILVER, 41L, null))
        .isInstanceOf(InvalidMemberException.class);
  }
}
