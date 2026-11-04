package com.kizuna.member.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailVerificationMailTest {

  @Test
  void ダイジェストは生値のSHA256の16進で_生値を含まない() {
    // 既知値: SHA-256("abc")
    assertThat(EmailVerificationMail.digest("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    assertThat(EmailVerificationMail.digest("token")).hasSize(64).doesNotContain("token");
  }
}
