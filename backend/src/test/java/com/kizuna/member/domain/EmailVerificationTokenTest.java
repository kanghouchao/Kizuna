package com.kizuna.member.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class EmailVerificationTokenTest {

  @Test
  void 発行は宛先とダイジェストを持ち_期限を発行から24時間に置き_未消費かつ生きている() {
    OffsetDateTime issuedAt = OffsetDateTime.of(2026, 11, 4, 9, 0, 0, 0, ZoneOffset.UTC);

    EmailVerificationToken token =
        EmailVerificationToken.issueFor(7L, "digest", "member@example.test", issuedAt);

    assertThat(token.getUserId()).isEqualTo(7L);
    assertThat(token.getTokenDigest()).isEqualTo("digest");
    assertThat(token.getEmail()).isEqualTo("member@example.test");
    assertThat(token.getIssuedAt()).isEqualTo(issuedAt);
    assertThat(token.getExpiresAt()).isEqualTo(issuedAt.plusHours(24));
    assertThat(token.getConsumedAt()).isNull();
    assertThat(token.getRevokedAt()).isNull();
  }
}
