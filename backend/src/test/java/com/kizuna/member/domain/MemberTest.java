package com.kizuna.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberTest {

  @Test
  @DisplayName("会員コードとプラットフォームユーザー ID を持って構築できる")
  void buildsWithCodeAndPlatformUserId() {
    Member member = Member.builder().memberCode("123456789012").platformUserId(7L).build();

    assertThat(member.getMemberCode()).isEqualTo("123456789012");
    assertThat(member.getPlatformUserId()).isEqualTo(7L);
  }

  @Test
  @DisplayName("会員コードが空だと不変条件違反で例外")
  void blankMemberCodeThrows() {
    assertThatThrownBy(() -> Member.builder().memberCode(" ").platformUserId(7L).build())
        .isInstanceOf(InvalidMemberException.class);
  }

  @Test
  @DisplayName("プラットフォームユーザー ID が無いと不変条件違反で例外")
  void missingPlatformUserIdThrows() {
    assertThatThrownBy(() -> Member.builder().memberCode("123456789012").build())
        .isInstanceOf(InvalidMemberException.class);
  }

  @Test
  @DisplayName("会員コードは数字 12 桁で生成される（先頭 0 も許容する固定長）")
  void generatedCodeIsTwelveDigits() {
    Random random = new Random(42);

    for (int i = 0; i < 100; i++) {
      assertThat(MemberCodes.generate(random)).matches("\\d{12}");
    }
  }
}
