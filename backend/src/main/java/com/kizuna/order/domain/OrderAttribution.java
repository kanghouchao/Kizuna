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

  /** 無効化の理由。訂正の根拠そのものなので無効化では必須で、有効な記録では null。 */
  @Column(name = "invalidated_reason", length = 500)
  private String invalidatedReason;

  /**
   * 無効化を実行した操作者。書き込み時は必須だが、読み出しでは欠落しうる — 操作者の削除で FK が SET NULL になるためで、会員参照（{@code
   * memberId}）と同じ紀律である。
   *
   * <p>非対称なのは意図で、「実行者不明のまま訂正を通す」ことと「訂正の後に操作者の口座が消える」ことは別の事象にあたる。前者は失効した認証セッションによる操作を記録に残せなくするので拒む。
   */
  @Column(name = "invalidated_by")
  private Long invalidatedBy;

  @Column(name = "invalidated_at")
  private OffsetDateTime invalidatedAt;

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

  /**
   * 伝票トークンの事後申領による帰属。完了時と違い、証明はトークンの所持だけで、店舗台帳（顧客行・関連）は一切動かない。
   *
   * <p>会員コードは申領した本人の現在のコードを写す（完了時のように関連のスナップショットを引き継ぐ余地は無い — その完了は誰にも帰属しなかった）。
   */
  public static OrderAttribution onReceiptClaim(
      String orderId, Long memberId, String memberCode, OffsetDateTime attributedAt) {
    return new OrderAttribution(
        orderId, memberId, memberCode, OrderAttributionSource.RECEIPT_TOKEN, attributedAt);
  }

  /**
   * 誤帰属を訂正する。帰属記録に対する唯一の訂正操作で、行は削除せず状態だけを倒し、理由・実行者・時刻を残す。
   *
   * <p>帰属そのものの記録（会員・根拠・帰属時刻）は書き換えない。訂正の対象は「誰の来店だったことにしていたか」であり、 それを消してしまうと監査に必要な事実が残らない。
   *
   * <p>ポイント台帳へは波及しない — 清算は理由の残る手動調整で行う（ADR 0009）。訂正を機械の級聯にすると、誤りの上に 誤りを重ねる危険が誤りを残す危険を上回る。
   *
   * <p>二度目の無効化は受け付けない。通せば初回の理由と実行者が上書きされ、最初の訂正の根拠が失われる。
   */
  public void invalidate(String reason, Long actorId, OffsetDateTime at) {
    if (status != OrderAttributionStatus.ACTIVE) {
      throw new InvalidOrderAttributionException("この帰属記録は既に無効化されています");
    }
    if (reason == null || reason.isBlank()) {
      throw new InvalidOrderAttributionException("無効化の理由は必須です");
    }
    if (actorId == null) {
      throw new InvalidOrderAttributionException("無効化の実行者は必須です");
    }
    if (at == null) {
      throw new InvalidOrderAttributionException("無効化の日時は必須です");
    }
    this.status = OrderAttributionStatus.INVALIDATED;
    this.invalidatedReason = reason;
    this.invalidatedBy = actorId;
    this.invalidatedAt = at;
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
