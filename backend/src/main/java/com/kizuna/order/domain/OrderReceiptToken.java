package com.kizuna.order.domain;

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
 * 完了時に会員へ帰属しなかった受注へ発行する、事後帰属のためのワンタイムの伝票トークン。所持そのものが証明となるため、 受注 ID のように列挙できる値では代替できない（受注 ID
 * は時刻順で辿れて秘密ではない）。
 *
 * <p>この行が持つのは<b>ダイジェストだけ</b>で、生値は発行の応答で一度返るきり残らない。照合はダイジェストで行うため、
 * バックアップや診断経路が未申領のクレデンシャルの漏えい経路にならない。
 *
 * <p>{@code plannedPoints} は完了時点の付与規則で確定した固定値。申領時点の設定は読まない — 同じ会計が申領の早い遅いで 別のポイントになることを許さないためで、0
 * 円完了にも 0 として発行する（申領の効果は来店の可視化に閉じる）。
 *
 * <p>帰属記録（{@link OrderAttribution}）と同じく platform 帰属で、店舗行分離機構には載せない — 申領するのは店舗文脈を
 * 持たない会員本人であり、店舗で絞ると照合そのものが成立しない。
 *
 * <p>不変条件（構築時に検証、違反は 400 系ドメイン例外 {@link InvalidOrderReceiptTokenException}）: 受注 ID・ダイジェスト・発行時刻が必須で、
 * 付与予定額は非負、構築直後は必ず ISSUED。
 */
@Entity
@Table(name = "t_order_receipt_tokens")
@Getter
@NoArgsConstructor
public class OrderReceiptToken extends BaseEntity {

  /** 申領期限（発行から）。予約の先読み上限（90 日）と対称の固定値。 */
  public static final Duration VALIDITY = Duration.ofDays(90);

  @Column(name = "order_id", nullable = false, updatable = false, length = 64)
  private String orderId;

  /** 生値の鍵付きハッシュ。生値そのものはどこにも永続化しない。 */
  @Column(name = "token_digest", nullable = false, updatable = false, unique = true, length = 64)
  private String tokenDigest;

  /** 完了時点の付与規則で確定した付与予定額。申領時に記帳する額であり、以後の設定変更に影響されない。 */
  @Column(name = "planned_points", nullable = false, updatable = false)
  private int plannedPoints;

  @Column(name = "issued_at", nullable = false, updatable = false)
  private OffsetDateTime issuedAt;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private OffsetDateTime expiresAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private OrderReceiptTokenStatus status;

  private OrderReceiptToken(
      String orderId, String tokenDigest, int plannedPoints, OffsetDateTime issuedAt) {
    if (orderId == null || orderId.isBlank()) {
      throw new InvalidOrderReceiptTokenException("受注 ID は必須です");
    }
    if (tokenDigest == null || tokenDigest.isBlank()) {
      throw new InvalidOrderReceiptTokenException("トークンのダイジェストは必須です");
    }
    if (plannedPoints < 0) {
      throw new InvalidOrderReceiptTokenException("付与予定額は 0 以上です");
    }
    if (issuedAt == null) {
      throw new InvalidOrderReceiptTokenException("発行の日時は必須です");
    }
    this.orderId = orderId;
    this.tokenDigest = tokenDigest;
    this.plannedPoints = plannedPoints;
    this.issuedAt = issuedAt;
    this.expiresAt = issuedAt.plus(VALIDITY);
    this.status = OrderReceiptTokenStatus.ISSUED;
  }

  /** 完了時の発行。申領期限は発行時刻から数える。 */
  public static OrderReceiptToken issueFor(
      String orderId, String tokenDigest, int plannedPoints, OffsetDateTime issuedAt) {
    return new OrderReceiptToken(orderId, tokenDigest, plannedPoints, issuedAt);
  }

  /** 生値もダイジェストも載せない。診断出力がクレデンシャルの漏えい経路にならないようにする。 */
  @Override
  public String toString() {
    return "OrderReceiptToken(id="
        + getId()
        + ", orderId="
        + orderId
        + ", plannedPoints="
        + plannedPoints
        + ", expiresAt="
        + expiresAt
        + ", status="
        + status
        + ")";
  }
}
