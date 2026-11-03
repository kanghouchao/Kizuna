package com.kizuna.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LineCommandToStringTest {

  @Test
  @DisplayName("認可の命令は toString に認可コードと code_verifier を出さない")
  void authorizationCommandMasksCodeAndVerifier() {
    String text =
        new LineAuthorizationCommand("auth-code", "https://app.test/cb", "verifier").toString();

    assertThat(text).doesNotContain("auth-code", "verifier").contains("https://app.test/cb");
  }

  @Test
  @DisplayName("登録の命令は toString に登録チケットを出さない")
  void registrationCommandMasksTicket() {
    String text = new LineRegistrationCommand("ticket-1", "会員太郎", "member@kizuna.test").toString();

    assertThat(text).doesNotContain("ticket-1").contains("会員太郎", "member@kizuna.test");
  }
}
