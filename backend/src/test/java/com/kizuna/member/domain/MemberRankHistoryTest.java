package com.kizuna.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberRankHistoryTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-29T12:00:00+09:00");
  private static final long ATTRIBUTION_ID = 88L;
  private static final long ENTRY_ID = 41L;

  @Test
  @DisplayName("会員・遷移前後・契機・時刻を持って構築できる")
  void buildsWithTheTransitionAndItsTrigger() {
    MemberRankHistory history =
        MemberRankHistory.promoted(
            7L, MemberRank.BRONZE, MemberRank.SILVER, ATTRIBUTION_ID, ENTRY_ID, NOW);

    assertThat(history.getMemberId()).isEqualTo(7L);
    assertThat(history.getPreviousRank()).isEqualTo(MemberRank.BRONZE);
    assertThat(history.getNewRank()).isEqualTo(MemberRank.SILVER);
    assertThat(history.getTriggeringAttributionId()).isEqualTo(ATTRIBUTION_ID);
    assertThat(history.getTriggeringEntryId()).isEqualTo(ENTRY_ID);
    assertThat(history.getPromotedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("付与仕訳の無い昇格も記録でき、契機の帰属記録だけが残ること")
  void recordsAPromotionThatCarriesNoGrantEntry() {
    MemberRankHistory history =
        MemberRankHistory.promoted(
            7L, MemberRank.BRONZE, MemberRank.SILVER, ATTRIBUTION_ID, null, NOW);

    assertThat(history.getTriggeringAttributionId()).isEqualTo(ATTRIBUTION_ID);
    assertThat(history.getTriggeringEntryId()).isNull();
  }

  @Test
  @DisplayName("下位・同格への遷移は記録できない（降格は存在しない）")
  void rejectsAnythingButAnUpgrade() {
    assertThatThrownBy(
            () ->
                MemberRankHistory.promoted(
                    7L, MemberRank.GOLD, MemberRank.SILVER, ATTRIBUTION_ID, ENTRY_ID, NOW))
        .isInstanceOf(InvalidMemberException.class);
    assertThatThrownBy(
            () ->
                MemberRankHistory.promoted(
                    7L, MemberRank.SILVER, MemberRank.SILVER, ATTRIBUTION_ID, ENTRY_ID, NOW))
        .isInstanceOf(InvalidMemberException.class);
    assertThatThrownBy(
            () ->
                MemberRankHistory.promoted(
                    7L, null, MemberRank.SILVER, ATTRIBUTION_ID, ENTRY_ID, NOW))
        .isInstanceOf(InvalidMemberException.class);
  }

  @Test
  @DisplayName("会員・契機の帰属記録・時刻の欠落は記録できない")
  void rejectsMissingIdentifiers() {
    assertThatThrownBy(
            () ->
                MemberRankHistory.promoted(
                    null, MemberRank.BRONZE, MemberRank.SILVER, ATTRIBUTION_ID, ENTRY_ID, NOW))
        .isInstanceOf(InvalidMemberException.class);
    assertThatThrownBy(
            () ->
                MemberRankHistory.promoted(
                    7L, MemberRank.BRONZE, MemberRank.SILVER, null, ENTRY_ID, NOW))
        .isInstanceOf(InvalidMemberException.class);
    assertThatThrownBy(
            () ->
                MemberRankHistory.promoted(
                    7L, MemberRank.BRONZE, MemberRank.SILVER, ATTRIBUTION_ID, ENTRY_ID, null))
        .isInstanceOf(InvalidMemberException.class);
  }
}
