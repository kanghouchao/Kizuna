package com.kizuna.order.domain;

import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

/**
 * 予約申請。受注（Order）とは別の付随記録で、店舗の確定時に CONFIRMED の Order を生成して {@code orderId} を回写する（ADR 0017）。
 *
 * <p>申請原文（希望内容）は終端に入った後も不変のまま残り、確定した受注の内容と対照できる。会員ポータルと公開店面の共用の受け皿で、
 * どちらの入口から来たかは会員コードのスナップショットの有無が表す（{@link #isGuest()}）。
 */
@Entity
@Table(name = "t_order_applications")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderApplication extends StoreScopedEntity {

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private OrderApplicationStatus status;

  /** 希望する利用日（営業日）。失効の導出はこの日と現在の営業日の比較だけで決まる。 */
  @Column(name = "business_date", nullable = false)
  private LocalDate businessDate;

  @Column(name = "arrival_scheduled_start_time")
  private LocalTime arrivalScheduledStartTime;

  @Column(name = "pax")
  private Integer pax;

  /** 希望する指名キャスト。null は指名なし。 */
  @Column(name = "cast_id")
  private String castId;

  @Column(name = "remarks", length = 500)
  private String remarks;

  /** 申請した会員。会員行の削除で FK が SET NULL になった後も、下のスナップショットで誰の申請かは読める。 */
  @Column(name = "requester_member_id")
  private Long requesterMemberId;

  /** 申請時点の会員コードのスナップショット。会員コードは発行後に変わらない。 */
  @Column(name = "requester_member_code", length = 20)
  private String requesterMemberCode;

  /**
   * 申請時に本人が店舗へ名乗った名前。確定時の自動整備で起こす台帳行の氏名になる。
   *
   * <p>店舗はプラットフォーム側プロフィール（表示名・メール）へ到達しないため、店舗が知る名前は本人がその店舗へ名乗ると決めたこの名前だけになる。
   */
  @Column(name = "requester_declared_name")
  private String requesterDeclaredName;

  /** ゲスト申請で本人が残した連絡先の氏名。会員申請では null（会員は名乗った名前を上の列が預かる）。 */
  @Column(name = "contact_name")
  private String contactName;

  /** ゲスト申請で本人が残した折返し先の電話番号。確定＝店舗が折返し連絡で内容を詰める操作なので、ゲスト申請では必須になる。 */
  @Column(name = "contact_phone_number", length = 50)
  private String contactPhoneNumber;

  /** 謝絶の理由。謝絶の根拠そのものなので謝絶では必須で、謝絶していない申請では null。分類軸ではない（enum 化しない）。 */
  @Column(name = "declined_reason", length = 500)
  private String declinedReason;

  /**
   * 終端遷移（確定・謝絶・取り下げ）の実行者。書き込み時は必須だが、読み出しでは欠落しうる — 操作者の削除で FK が SET NULL になるためで、受注の {@code
   * cancelledBy} と同じ紀律である。
   */
  @Column(name = "processed_by")
  private Long processedBy;

  @Column(name = "processed_at")
  private OffsetDateTime processedAt;

  /** 確定時に生成した受注の回写。確定していない申請では null（シフトの申請行 shift_id 回写と同じ背骨）。 */
  @Column(name = "order_id")
  private String orderId;

  /**
   * 公開店面からのゲスト申請か。判定を会員コードのスナップショットで行うのは、会員行が削除されて {@code requesterMemberId} が欠落した会員申請を
   * ゲスト扱いへ倒さないため（会員コードは発行後に変わらず、削除でも消えない）。
   */
  public boolean isGuest() {
    return requesterMemberCode == null;
  }

  /**
   * 申請者の会員参照を外す。会員行の削除に伴う FK の SET NULL と同じ意味で、会員コードのスナップショットは残す。
   *
   * <p>誰の申請だったかは残り続けるため、未処理の申請は会員が消えた後も店舗が処理し終えられる。
   */
  public void detachRequesterMember() {
    this.requesterMemberId = null;
  }

  /**
   * 失効の導出。希望日を過ぎても店舗が処理していない PENDING は失効として扱う（表示と確定・謝絶の拒否だけで、行の状態は動かさない。 先例は欠勤導出 — 存在しない事実を状態で持たず、
   * 判定のたびに導く）。
   *
   * <p>比較は営業日で行う。暦日 0 時ではなく日付変更時刻で「過ぎた」が決まるため、現在の営業日は呼出側（application 層）が渡す。
   */
  public static boolean isExpired(
      OrderApplicationStatus status, LocalDate businessDate, LocalDate currentBusinessDate) {
    return status == OrderApplicationStatus.PENDING && businessDate.isBefore(currentBusinessDate);
  }

  public boolean isExpired(LocalDate currentBusinessDate) {
    return isExpired(status, businessDate, currentBusinessDate);
  }

  /**
   * 確定・謝絶できる状態かを検める。終端の申請と失効した申請を撥ねる。
   *
   * <p>公開しているのは「検証は書き換えより先に」の紀律のため — 確定は先に受注を起こしてから回写するので、 受注を起こす前にこの判定だけを通す必要がある。
   */
  public void ensureDecidable(LocalDate currentBusinessDate) {
    if (status != OrderApplicationStatus.PENDING) {
      throw new InvalidOrderApplicationOperationException("処理済みの予約申請です（" + status + "）");
    }
    if (isExpired(currentBusinessDate)) {
      throw new InvalidOrderApplicationOperationException("希望日を過ぎた予約申請は確定・謝絶できません（失効）");
    }
  }

  /** 確定する。生成した受注の id を回写し、実行者と時刻を残す。対象は失効していない PENDING のみ。 */
  public void confirmWith(
      String orderId, Long actorId, OffsetDateTime at, LocalDate currentBusinessDate) {
    ensureDecidable(currentBusinessDate);
    if (orderId == null) {
      throw new InvalidOrderApplicationOperationException("確定には生成した受注の id が必須です");
    }
    requireActorAndTime(actorId, at);
    this.status = OrderApplicationStatus.CONFIRMED;
    this.orderId = orderId;
    this.processedBy = actorId;
    this.processedAt = at;
  }

  /** 謝絶する。理由は必須（取消 ADR 0013 の先例に倣う）。対象は失効していない PENDING のみ。 */
  public void decline(
      String reason, Long actorId, OffsetDateTime at, LocalDate currentBusinessDate) {
    ensureDecidable(currentBusinessDate);
    if (reason == null || reason.isBlank()) {
      throw new InvalidOrderApplicationOperationException("謝絶の理由は必須です");
    }
    requireActorAndTime(actorId, at);
    this.status = OrderApplicationStatus.DECLINED;
    this.declinedReason = reason;
    this.processedBy = actorId;
    this.processedAt = at;
  }

  /** 本人が取り下げる。失効した PENDING も取り下げられる（本人の整理を妨げる理由が無い）。 */
  public void withdraw(Long actorId, OffsetDateTime at) {
    if (status != OrderApplicationStatus.PENDING) {
      throw new InvalidOrderApplicationOperationException("処理済みの予約申請は取り下げられません（" + status + "）");
    }
    requireActorAndTime(actorId, at);
    this.status = OrderApplicationStatus.WITHDRAWN;
    this.processedBy = actorId;
    this.processedAt = at;
  }

  private static void requireActorAndTime(Long actorId, OffsetDateTime at) {
    if (actorId == null) {
      throw new InvalidOrderApplicationOperationException("実行者は必須です");
    }
    if (at == null) {
      throw new InvalidOrderApplicationOperationException("実行日時は必須です");
    }
  }

  @Override
  public String toString() {
    return "OrderApplication(id="
        + getId()
        + ", businessDate="
        + businessDate
        + ", status="
        + status
        + ")";
  }
}
