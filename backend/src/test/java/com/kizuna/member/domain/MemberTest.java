package com.kizuna.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberTest {

  @Test
  @DisplayName("会員コードとユーザー ID を持って構築できる")
  void buildsWithCodeAndUserId() {
    Member member = Member.builder().memberCode("123456789012").userId(7L).build();

    assertThat(member.getMemberCode()).isEqualTo("123456789012");
    assertThat(member.getUserId()).isEqualTo(7L);
  }

  @Test
  @DisplayName("会員コードが空だと不変条件違反で例外")
  void blankMemberCodeThrows() {
    assertThatThrownBy(() -> Member.builder().memberCode(" ").userId(7L).build())
        .isInstanceOf(InvalidMemberException.class);
  }

  @Test
  @DisplayName("ユーザー ID が無いと不変条件違反で例外")
  void missingUserIdThrows() {
    assertThatThrownBy(() -> Member.builder().memberCode("123456789012").build())
        .isInstanceOf(InvalidMemberException.class);
  }

  @Test
  @DisplayName("新規会員は最低位の BRONZE から始まる（builder が指定できる余地を持たない）")
  void newMemberStartsAtBronze() {
    Member member = Member.builder().memberCode("123456789012").userId(7L).build();

    assertThat(member.getRank()).isEqualTo(MemberRank.BRONZE);
  }

  @Test
  @DisplayName("昇格は上位へのみ通り、同格・降格・null は不変条件違反で例外")
  void promoteToRejectsAnythingButAnUpgrade() {
    Member member = Member.builder().memberCode("123456789012").userId(7L).build();
    member.promoteTo(MemberRank.GOLD);

    assertThat(member.getRank()).isEqualTo(MemberRank.GOLD);
    assertThatThrownBy(() -> member.promoteTo(MemberRank.GOLD))
        .isInstanceOf(InvalidMemberException.class);
    assertThatThrownBy(() -> member.promoteTo(MemberRank.SILVER))
        .isInstanceOf(InvalidMemberException.class);
    assertThatThrownBy(() -> member.promoteTo(null)).isInstanceOf(InvalidMemberException.class);
  }

  @Test
  @DisplayName("会員コードは数字 12 桁で生成される（先頭 0 も許容する固定長）")
  void generatedCodeIsTwelveDigits() {
    Random random = new Random(42);

    for (int i = 0; i < 100; i++) {
      assertThat(MemberCodes.generate(random)).matches("\\d{12}");
    }
  }

  @Test
  @DisplayName("退会は理由・操作者・日時を記録し、理由は前後の空白を落として 500 文字以内")
  void withdrawRecordsReasonActorAndTime() {
    Member member = Member.builder().memberCode("123456789012").userId(7L).build();
    var at = OffsetDateTime.parse("2026-10-26T10:00:00+09:00");

    member.withdraw("  引っ越しのため  ", 7L, at);

    assertThat(member.isWithdrawn()).isTrue();
    assertThat(member.getWithdrawnAt()).isEqualTo(at);
    assertThat(member.getWithdrawalReason()).isEqualTo("引っ越しのため");
    assertThat(member.getWithdrawnBy()).isEqualTo(7L);
  }

  @Test
  @DisplayName("理由が空・長すぎ・操作者や日時が無い退会は 400 系の不変条件違反")
  void withdrawRejectsMissingReasonActorOrTime() {
    var at = OffsetDateTime.now();
    assertThatThrownBy(() -> fresh().withdraw(" ", 7L, at))
        .isInstanceOf(InvalidMemberException.class);
    assertThatThrownBy(() -> fresh().withdraw("x".repeat(501), 7L, at))
        .isInstanceOf(InvalidMemberException.class);
    assertThatThrownBy(() -> fresh().withdraw("理由", null, at))
        .isInstanceOf(InvalidMemberException.class);
    assertThatThrownBy(() -> fresh().withdraw("理由", 7L, null))
        .isInstanceOf(InvalidMemberException.class);
  }

  @Test
  @DisplayName("退会済みの再退会は 409（終端状態の繰り返し）")
  void withdrawTwiceConflicts() {
    Member member = fresh();
    member.withdraw("理由", 7L, OffsetDateTime.now());

    assertThatThrownBy(() -> member.withdraw("再度", 7L, OffsetDateTime.now()))
        .isInstanceOf(MemberAlreadyWithdrawnException.class);
  }

  private static Member fresh() {
    return Member.builder().memberCode("123456789012").userId(7L).build();
  }
}
