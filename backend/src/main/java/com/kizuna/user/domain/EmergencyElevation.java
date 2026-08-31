package com.kizuna.user.domain;

import com.kizuna.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 緊急昇格の発動 1 回分の記録。1 発動 = 1 店舗・理由必須・有効期間は固定 60 分の追記型で、書き換えも削除もしない —
 * 再発動は新しい行になる。担当店舗集合を迂回して店舗のデータへ届く唯一の経路なので、誰が・どの店舗へ・いつ・なぜ 届いたかを後から復元できることが記録の存在理由である（取捨の全文は ADR
 * 0024）。
 *
 * <p>列名を {@code store_id} にしない（{@code target_store_id}）のは、昇格の宛先であって行分離の作用域ではないという宣言。 {@code
 * activatedAt} / {@code expiresAt} を明示列に持つのは、効力区間の正本を行の書き込み時刻に負わせないためである。
 */
@Entity
@Table(name = "t_emergency_elevations")
@Getter
@NoArgsConstructor
public class EmergencyElevation extends BaseEntity {

  /** 昇格の有効期間（発動から）。運用上の裁定として固定した業務規則で、設定値ではない。 */
  public static final Duration EFFECTIVE_DURATION = Duration.ofMinutes(60);

  @Column(name = "activated_by", nullable = false, updatable = false)
  private Long activatedBy;

  @Column(name = "target_store_id", nullable = false, updatable = false)
  private Long targetStoreId;

  @Column(name = "reason", nullable = false, updatable = false, length = 500)
  private String reason;

  @Column(name = "activated_at", nullable = false, updatable = false)
  private OffsetDateTime activatedAt;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private OffsetDateTime expiresAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private EmergencyElevationStatus status;

  /** 撤回を実行した操作者。撤回時は必須で、有効な記録では null。 */
  @Column(name = "revoked_by")
  private Long revokedBy;

  @Column(name = "revoked_at")
  private OffsetDateTime revokedAt;

  private EmergencyElevation(
      Long activatedBy, Long targetStoreId, String reason, OffsetDateTime activatedAt) {
    if (activatedBy == null) {
      throw new InvalidEmergencyElevationException("発動の実行者は必須です");
    }
    if (targetStoreId == null) {
      throw new InvalidEmergencyElevationException("昇格の対象店舗は必須です");
    }
    if (reason == null || reason.isBlank()) {
      throw new InvalidEmergencyElevationException("発動の理由は必須です");
    }
    if (activatedAt == null) {
      throw new InvalidEmergencyElevationException("発動の日時は必須です");
    }
    this.activatedBy = activatedBy;
    this.targetStoreId = targetStoreId;
    this.reason = reason;
    this.activatedAt = activatedAt;
    this.expiresAt = activatedAt.plus(EFFECTIVE_DURATION);
    this.status = EmergencyElevationStatus.ACTIVE;
  }

  /** 昇格を発動する。期限は発動時刻から数え、以後どの操作でも延長されない。 */
  public static EmergencyElevation activate(
      Long activatedBy, Long targetStoreId, String reason, OffsetDateTime at) {
    return new EmergencyElevation(activatedBy, targetStoreId, reason, at);
  }

  /**
   * 昇格を撤回する。有効かつ期限内のときだけ通り、二度目も期限切れ後も撥ねる（期限の瞬間も撥ねる側）。
   * 期限切れ後の版の増分は発動者の通常セッションを巻き添えにするだけで昇格を縮めず、二度目を通すと 初回の撤回者・時刻が上書きされて最初の撤回の根拠が失われる。
   */
  public void revoke(Long revokedBy, OffsetDateTime at) {
    if (revokedBy == null) {
      throw new InvalidEmergencyElevationException("撤回の実行者は必須です");
    }
    if (status != EmergencyElevationStatus.ACTIVE || !at.isBefore(expiresAt)) {
      throw new InvalidEmergencyElevationException("この緊急昇格は撤回できる状態ではありません");
    }
    this.status = EmergencyElevationStatus.REVOKED;
    this.revokedBy = revokedBy;
    this.revokedAt = at;
  }

  @Override
  public String toString() {
    return "EmergencyElevation(id="
        + getId()
        + ", activatedBy="
        + activatedBy
        + ", targetStoreId="
        + targetStoreId
        + ", expiresAt="
        + expiresAt
        + ", status="
        + status
        + ")";
  }
}
