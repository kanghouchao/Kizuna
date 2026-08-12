package com.kizuna.order.domain;

import com.kizuna.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 受注 1 件が会員へ帰属した事実。帰属が生まれる瞬間は完了と事後申領の 2 つだけで（{@link OrderAttributionSource}）、成立後は関連（Customer–Member
 * Link）の状態に影響されない不変の事実である。会員の来店履歴はこの記録だけから読み、関連の区間を読み直さない。
 *
 * <p>会員はプラットフォーム級の身分であり、来店履歴が店舗を跨いで読めることが正しさの条件のため、台帳（{@code t_point_entries}）と同じく platform
 * 帰属とし店舗行分離機構には載せない。店舗列を持たないのはこの意味論の宣言でもある — 表示に要る店舗名・日付・人数・担当は受注への JOIN で導出する。
 *
 * <p>{@code memberCode} は帰属時点のスナップショットで、会員行が消えて {@code memberId} が欠落した後も誰の来店だったかを読めるようにする。
 *
 * <p>不変条件（構築時に検証、違反は 400 系ドメイン例外 {@link InvalidOrderAttributionException}）: 受注 ID・会員
 * ID・会員コード・帰属日時が必須で、 構築直後は必ず ACTIVE。
 */
@Entity
@Table(name = "t_order_attributions")
@Getter
@NoArgsConstructor
public class OrderAttribution extends BaseEntity {

  @Column(name = "order_id", nullable = false, updatable = false, length = 64)
  private String orderId;

  /** 帰属先の会員。会員削除後も記録を残すため欠落しうる。 */
  @Column(name = "member_id", updatable = false)
  private Long memberId;

  @Column(name = "member_code", nullable = false, updatable = false, length = 20)
  private String memberCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false, updatable = false, length = 30)
  private OrderAttributionSource source;

  @Column(name = "attributed_at", nullable = false, updatable = false)
  private OffsetDateTime attributedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private OrderAttributionStatus status;

  private OrderAttribution(
      String orderId,
      Long memberId,
      String memberCode,
      OrderAttributionSource source,
      OffsetDateTime attributedAt) {
    if (orderId == null || orderId.isBlank()) {
      throw new InvalidOrderAttributionException("受注 ID は必須です");
    }
    if (memberId == null) {
      throw new InvalidOrderAttributionException("会員 ID は必須です");
    }
    if (memberCode == null || memberCode.isBlank()) {
      throw new InvalidOrderAttributionException("会員コードは必須です");
    }
    if (attributedAt == null) {
      throw new InvalidOrderAttributionException("帰属の日時は必須です");
    }
    this.orderId = orderId;
    this.memberId = memberId;
    this.memberCode = memberCode;
    this.source = source;
    this.attributedAt = attributedAt;
    this.status = OrderAttributionStatus.ACTIVE;
  }

  /** 完了時の会員解決による帰属。会計金額が 0 でも成立する — 帰属は来店可視性の事実であり、ポイントの有無とは独立している。 */
  public static OrderAttribution onCompletion(
      String orderId, Long memberId, String memberCode, OffsetDateTime attributedAt) {
    return new OrderAttribution(
        orderId, memberId, memberCode, OrderAttributionSource.COMPLETION, attributedAt);
  }

  @Override
  public String toString() {
    return "OrderAttribution(id="
        + getId()
        + ", orderId="
        + orderId
        + ", memberCode="
        + memberCode
        + ", source="
        + source
        + ", status="
        + status
        + ")";
  }
}
