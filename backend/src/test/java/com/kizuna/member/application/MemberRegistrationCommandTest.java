package com.kizuna.member.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberRegistrationCommandTest {

  @Test
  @DisplayName("会員登録の命令は文字列化しても生のパスワードを含まない")
  void hidesPassword() {
    var command = new MemberRegistrationCommand("member@kizuna.test", "s3cret-raw", "会員 花子");

    assertThat(command.toString()).doesNotContain("s3cret-raw").contains("member@kizuna.test");
  }
}
