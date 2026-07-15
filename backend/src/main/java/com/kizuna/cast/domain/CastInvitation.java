package com.kizuna.cast.domain;

import com.kizuna.shared.persistence.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

/** キャスト招待集約。1 档案につき有効な招待は最新の 1 枚のみ（再発行で旧 PENDING は失効する）。 リンク発行後の受諾で {@link Cast} に平台身分を紐づける。 */
@Entity
@Table(name = "t_cast_invitations")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CastInvitation extends TenantScopedEntity {

  /** 招待リンクの有効期間（ドメイン所有の定数）。 */
  public static final Duration VALIDITY = Duration.ofHours(72);

  @Column(name = "cast_id", nullable = false, length = 64)
  private String castId;

  @Column(name = "token", nullable = false, unique = true, length = 64)
  private String token;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  @Builder.Default
  private Status status = Status.PENDING;

  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  @Column(name = "accepted_at")
  private OffsetDateTime acceptedAt;

  /** 招待の物理ライフサイクル状態。 */
  public enum Status {
    PENDING,
    ACCEPTED,
    INVALIDATED
  }

  /** PENDING かつ未期限の招待のみ受諾できる。それ以外は状態例外を投げる。 */
  public void accept(OffsetDateTime now) {
    if (status != Status.PENDING) {
      throw new CastInvitationStateException("この招待は既に受諾済みまたは失効しています");
    }
    if (isExpired(now)) {
      throw new CastInvitationStateException("この招待は有効期限が切れています");
    }
    this.status = Status.ACCEPTED;
    this.acceptedAt = now;
  }

  /** PENDING の招待のみ失効させる（再発行時の旧票失効）。それ以外は状態例外を投げる。 */
  public void invalidate() {
    if (status != Status.PENDING) {
      throw new CastInvitationStateException("PENDING の招待のみ失効できます");
    }
    this.status = Status.INVALIDATED;
  }

  /** 指定時刻が有効期限を過ぎているか。期限ちょうどは未期限として扱う。 */
  public boolean isExpired(OffsetDateTime now) {
    return now.isAfter(expiresAt);
  }
}
