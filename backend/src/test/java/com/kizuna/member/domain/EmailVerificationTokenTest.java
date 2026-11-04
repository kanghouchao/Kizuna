package com.kizuna.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

  @Test
  void 期限ちょうどまでは使え_過ぎれば使えない() {
    OffsetDateTime issuedAt = OffsetDateTime.of(2026, 11, 4, 9, 0, 0, 0, ZoneOffset.UTC);
    EmailVerificationToken token =
        EmailVerificationToken.issueFor(7L, "digest", "member@example.test", issuedAt);

    assertThat(token.isUsable(issuedAt.plusHours(24))).isTrue();
    assertThat(token.isUsable(issuedAt.plusHours(24).plusNanos(1000))).isFalse();
  }

  @Test
  void 消費したトークンは使えず_二度目の消費は不変条件違反() {
    OffsetDateTime issuedAt = OffsetDateTime.of(2026, 11, 4, 9, 0, 0, 0, ZoneOffset.UTC);
    EmailVerificationToken token =
        EmailVerificationToken.issueFor(7L, "digest", "member@example.test", issuedAt);

    token.consume(issuedAt.plusMinutes(5));

    assertThat(token.getConsumedAt()).isEqualTo(issuedAt.plusMinutes(5));
    assertThat(token.isUsable(issuedAt.plusMinutes(6))).isFalse();
    assertThatThrownBy(() -> token.consume(issuedAt.plusMinutes(6)))
        .isInstanceOf(IllegalStateException.class);
  }
}
