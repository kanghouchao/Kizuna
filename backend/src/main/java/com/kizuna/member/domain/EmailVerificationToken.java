package com.kizuna.member.domain;

import com.kizuna.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * メールアドレスの検証トークン（ADR 0040）。行が持つのはダイジェストだけで、生値は検証メールの本文に一度載るきり残らない。
 *
 * <p>トークンは発行時の宛先（{@code email}）に束縛される。宛先が変われば古いトークンで新しい宛先を検証済みにはできない。
 */
@Entity
@Table(name = "t_email_verification_tokens")
@Getter
@NoArgsConstructor
public class EmailVerificationToken extends BaseEntity {

  public static final Duration VALIDITY = Duration.ofHours(24);

  @Column(name = "user_id", nullable = false, updatable = false)
  private Long userId;

  @Column(name = "token_digest", nullable = false, updatable = false, unique = true, length = 64)
  private String tokenDigest;

  @Column(name = "email", nullable = false, updatable = false, length = 255)
  private String email;

  @Column(name = "issued_at", nullable = false, updatable = false)
  private OffsetDateTime issuedAt;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private OffsetDateTime expiresAt;

  @Column(name = "consumed_at")
  private OffsetDateTime consumedAt;

  @Column(name = "revoked_at")
  private OffsetDateTime revokedAt;

  private EmailVerificationToken(
      Long userId, String tokenDigest, String email, OffsetDateTime issuedAt) {
    this.userId = userId;
    this.tokenDigest = tokenDigest;
    this.email = email;
    this.issuedAt = issuedAt;
    this.expiresAt = issuedAt.plus(VALIDITY);
  }

  /** 宛先へ発行する。有効期限は発行時刻から数える。 */
  public static EmailVerificationToken issueFor(
      Long userId, String tokenDigest, String email, OffsetDateTime issuedAt) {
    return new EmailVerificationToken(userId, tokenDigest, email, issuedAt);
  }

  /** 消費・失効の前で、期限内か。期限ちょうどは期限内として扱う。 */
  public boolean isUsable(OffsetDateTime now) {
    return consumedAt == null && revokedAt == null && !now.isAfter(expiresAt);
  }

  /** 消費する。消費・失効の済んだトークンは二度と使えない。 */
  public void consume(OffsetDateTime at) {
    if (consumedAt != null || revokedAt != null) {
      throw new IllegalStateException("消費・失効の済んだ検証トークンは消費できません");
    }
    this.consumedAt = at;
  }
}
