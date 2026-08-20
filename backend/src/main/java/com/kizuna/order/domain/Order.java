package com.kizuna.order.domain;

import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "t_orders")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order extends StoreScopedEntity {

  /** 完了処理が書くポイント利用の明細の名称。行の名称は写しなので、機構が起こす行では固定の一語を持つ。 */
  private static final String POINT_REDEMPTION_LINE_NAME = "ポイント利用";

  @Column(name = "receptionist_id")
  private Long receptionistId;

  @Column(name = "business_date", nullable = false)
  private LocalDate businessDate;

  @Column(name = "arrival_scheduled_start_time")
  private LocalTime arrivalScheduledStartTime;

  @Column(name = "arrival_scheduled_end_time")
  private LocalTime arrivalScheduledEndTime;

  @Column(name = "customer_id")
  private String customerId;

  /**
   * 受付で録入された連絡先の氏名。台帳の顧客に着かなかった受注にだけ入る。
   *
   * <p>顧客が着いた受注では台帳の行が連絡先を持つのでここは空のままで、名乗りの正本は常に台帳の側にある。
   */
  @Column(name = "contact_name")
  private String contactName;

  /** 受付で録入された連絡先の電話番号。{@link #contactName} と同じく顧客が着かなかった受注にだけ入る。 */
  @Column(name = "contact_phone_number", length = 50)
  private String contactPhoneNumber;

  @Column(name = "cast_id")
  private String castId;

  @Column(name = "pax")
  private Integer pax;

  /**
   * この受注に実際に適用されたコースの名称の写し。確定（出生）で写り、終端に入るまでは店舗が更新できる — 確定の瞬間を凍結した帧ではなく「実際に適用された内容」を指す。
   *
   * <p>コース分数・延長分数も同じ写しの語義で、時間の側を担う。サービス定義の側からこれらの列へ回写することは無い。
   */
  @Column(name = "course_name")
  private String courseName;

  @Column(name = "course_minutes")
  private Integer courseMinutes;

  @Column(name = "extension_minutes")
  private Integer extensionMinutes;

  @Column(name = "carrier")
  private String carrier;

  @Column(name = "media_name")
  private String mediaName;

  /**
   * 会計金額。明細行の帯符号金額の単純総和であり、手入力の口は無い — 行を動かす行為メソッドが毎回書き直す。
   *
   * <p>列として持つのは一覧・集計が行を畳まずに読めるようにするためで、正本は行の側にある。
   */
  @Column(name = "total_fee")
  @Builder.Default
  private Integer totalFee = 0;

  /**
   * 受注金額の内訳。1 行＝種別＋名称の写し＋帯符号金額で、{@link #totalFee} はこの総和として導出される。
   *
   * <p>{@code nullable = false} は子側 INSERT に order_id を含めさせるための指定で、これが無いと NULL で挿入してから UPDATE
   * する経路になり NOT NULL 制約に触れる。
   *
   * <p>並びは永続化された行の id 順（＝行が起きた順）で、差し替え途中のメモリ上の並びは契約ではない。
   */
  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "order_id", nullable = false)
  @OrderBy("id")
  @Getter(AccessLevel.NONE)
  @Builder.Default
  private List<OrderFeeLine> feeLines = new ArrayList<>();

  /** 会計に伴い自動付与したポイント。完了処理でのみ確定する（台帳の加算仕訳と対になる記録）。 */
  @Column(name = "auto_grant_points")
  private Integer autoGrantPoints;

  @Column(name = "survey_status")
  private String surveyStatus;

  @Column(name = "location_address")
  private String locationAddress;

  @Column(name = "location_building")
  private String locationBuilding;

  @Column(name = "actual_arrival_time")
  private LocalTime actualArrivalTime;

  @Column(name = "actual_end_time")
  private LocalTime actualEndTime;

  @Column(name = "remarks")
  private String remarks;

  @Column(name = "cast_driver_message")
  private String castDriverMessage;

  @Enumerated(EnumType.STRING)
  @Column(name = "status")
  private OrderStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "reception_route", length = 20)
  private ReceptionRoute receptionRoute;

  /** 取消の理由。取消の根拠そのものなので取消では必須で、取消していない受注では null。分類軸ではない（enum 化しない）。 */
  @Column(name = "cancelled_reason", length = 500)
  private String cancelledReason;

  /**
   * 取消を実行した操作者。書き込み時は必須だが、読み出しでは欠落しうる — 操作者の削除で FK が SET NULL になるためで、帰属記録の {@code invalidatedBy}
   * と同じ紀律である。
   */
  @Column(name = "cancelled_by")
  private Long cancelledBy;

  @Column(name = "cancelled_at")
  private OffsetDateTime cancelledAt;

  /** 申請した会員。予約申請（OrderApplication）の確定で生まれた受注だけが持ち、店舗が直接起こした受注では null。 */
  @Column(name = "requester_member_id")
  private Long requesterMemberId;

  /** 申請時点の会員コードのスナップショット。会員行が消えて requesterMemberId が欠落した後も申請者を読めるようにする。 */
  @Column(name = "requester_member_code", length = 20)
  private String requesterMemberCode;

  /**
   * 申請時に本人が店舗へ名乗った名前の写し（正本は申請行と、確定時の自動整備が起こした台帳行）。
   *
   * <p>店舗はプラットフォーム側プロフィール（表示名・メール）へ到達しないため、店舗が知る名前は本人がその店舗へ名乗ると決めたこの名前だけになる。
   */
  @Column(name = "requester_declared_name")
  private String requesterDeclaredName;

  /** キャストを割り当てる（存在確認は application 層の責務）。 */
  public void assignCast(String castId) {
    this.castId = castId;
  }

  /** 受付担当者を割り当てる（存在確認は application 層の責務）。 */
  public void assignReceptionist(Long receptionistId) {
    this.receptionistId = receptionistId;
  }

  /** 顧客を紐付ける（存在確認・検索/作成は application 層の責務）。 */
  public void linkCustomer(String customerId) {
    this.customerId = customerId;
  }

  /**
   * 顧客が着いていなければ、受付で録入された連絡先を受注に写す。着いている受注では何もしない。
   *
   * <p>受注は本来 連絡先を自身に持たず、顧客の名乗りは台帳の行が引き受ける。それでも写しを残すのは、 電話番号が同店の複数の顧客に一致して自動照合を断念した受注（無帰属受注は正規の状態）で、
   * 写しが無いと録入された氏名・電話番号がどこにも残らず、店舗が折り返す手立てを失うため。
   *
   * <p>着いているかの判定を呼び手に委ねず自分で見るのは、判定の材料である顧客参照をこの集約が持っているから。
   * 委ねると「台帳の行と受注の写しが両方名乗る」状態を誰でも作れてしまい、どちらが正本かが読み手から消える。
   */
  public void recordContactIfUnlinked(String name, String phoneNumber) {
    if (customerId != null) {
      return;
    }
    this.contactName = name;
    this.contactPhoneNumber = phoneNumber;
  }

  /**
   * 受付で録入された連絡先を訂正する。null のフィールドは変更しない。
   *
   * <p>顧客が着いた受注では<b>撥ねる</b>。着いていれば名乗りの正本は台帳の行であり、受注側の写しを書き足すと どちらが正本かが読み手から消える。黙って捨てる（{@link
   * #recordContactIfUnlinked} の作成時の作法）を 訂正の経路でも採ると、送り手は直ったと誤解したまま台帳の誤記が残る。
   *
   * <p>訂正は写しを直すだけで、台帳照合（0 件建档 / 1 件紐づけ / 複数断念）は再走しない。事後に受注を顧客へ着ける操作は この集約の外の別の口が担う。
   */
  public void correctContact(String name, String phoneNumber) {
    if (customerId != null) {
      throw new InvalidOrderContactCorrectionException("顧客が紐づいた受注の連絡先は編集できません。顧客詳細で台帳の情報を訂正してください");
    }
    if (name != null) {
      this.contactName = name;
    }
    if (phoneNumber != null) {
      this.contactPhoneNumber = phoneNumber;
    }
  }

  /** 部分更新コマンドを適用する。null のフィールドは変更しない。 */
  public void apply(OrderPatch patch) {
    if (patch.businessDate() != null) {
      this.businessDate = patch.businessDate();
    }
    if (patch.arrivalScheduledStartTime() != null) {
      this.arrivalScheduledStartTime = patch.arrivalScheduledStartTime();
    }
    if (patch.arrivalScheduledEndTime() != null) {
      this.arrivalScheduledEndTime = patch.arrivalScheduledEndTime();
    }
    if (patch.pax() != null) {
      this.pax = patch.pax();
    }
    if (patch.courseName() != null) {
      this.courseName = patch.courseName();
    }
    if (patch.courseMinutes() != null) {
      this.courseMinutes = patch.courseMinutes();
    }
    if (patch.extensionMinutes() != null) {
      this.extensionMinutes = patch.extensionMinutes();
    }
    // 明細はコース名より後に当てる。基本コース料金の行名称はコース名の写しから採るため、
    // 順序が逆だと同じ要求で送られた新しいコース名が行に載らない。
    if (patch.feeLines() != null) {
      replaceStoreFeeLines(patch.feeLines());
    }
    if (patch.locationAddress() != null) {
      this.locationAddress = patch.locationAddress();
    }
    if (patch.locationBuilding() != null) {
      this.locationBuilding = patch.locationBuilding();
    }
    if (patch.carrier() != null) {
      this.carrier = patch.carrier();
    }
    if (patch.mediaName() != null) {
      this.mediaName = patch.mediaName();
    }
    if (patch.remarks() != null) {
      this.remarks = patch.remarks();
    }
    if (patch.castDriverMessage() != null) {
      this.castDriverMessage = patch.castDriverMessage();
    }
  }

  /**
   * この受注の明細行。差し替えは {@link #replaceStoreFeeLines} と {@link #completeWith} だけが行うため、読み手には変更できない写しを返す。
   */
  public List<OrderFeeLine> getFeeLines() {
    return List.copyOf(feeLines);
  }

  /**
   * 店舗が手入力する明細を丸ごと差し替える。行に同一性は持たせず、送られた内容がそのまま新しい内訳になる。
   *
   * <p>システム専有の行（ポイント利用）は要求に含められず、既にある行はこの経路で消えない。台帳の減算仕訳と対で書かれた記録が 通常の編集で外れると、内訳と台帳が黙って食い違う。
   *
   * <p>基本コース料金の行名称は受注のコース名の写しから採る。行の側にも名前を名乗らせると、同じ受注が二つのコース名を主張する。
   */
  public void replaceStoreFeeLines(List<OrderFeeLineDraft> drafts) {
    List<OrderFeeLine> replaced = new ArrayList<>();
    for (OrderFeeLineDraft draft : drafts) {
      if (draft.kind() != null && draft.kind().isSystemOwned()) {
        throw new InvalidOrderFeeLineException("ポイント利用の明細は完了処理だけが書けます");
      }
      replaced.add(OrderFeeLine.of(draft.kind(), lineNameFor(draft), draft.amount()));
    }
    feeLines.removeIf(line -> !line.getKind().isSystemOwned());
    feeLines.addAll(replaced);
    recalculateTotalFee();
  }

  private String lineNameFor(OrderFeeLineDraft draft) {
    if (draft.kind() != OrderFeeLineKind.BASE_COURSE) {
      return draft.name();
    }
    if (courseName == null || courseName.isBlank()) {
      throw new InvalidOrderFeeLineException("基本コース料金の明細にはコース名が必要です");
    }
    return courseName;
  }

  /** 合計を明細から取り直す。行を動かす経路はすべてここを通り、和と合計が食い違う状態を集約の外から作れないようにする。 */
  private void recalculateTotalFee() {
    this.totalFee = feeLines.stream().mapToInt(OrderFeeLine::getAmount).sum();
  }

  /**
   * 会計を確定して注文を完了する。確認済みの注文のみ完了でき、ポイント利用の明細と自動付与ポイントはこの経路でのみ確定する。
   *
   * <p>会計金額は引数で受けない — 合計は明細の総和として既に決まっている。利用ポイントは減算の明細行として内訳へ入り、
   * 合計はそのぶん下がる（客が現金で払う額が合計になる）。付与の基準と利用の上限は、この行が入る前の総和を呼出側が読んで決める。
   *
   * <p>同一状態への静默冪等（{@link #transitionTo}）に委ねず、完了済みを明示的に撥ねる。完了は台帳記帳と不可分のため、
   * 二度目の呼出を黙って通すと同じ受注で付与・利用が二重に記帳される。
   */
  public void completeWith(int usedPoints, int autoGrantPoints) {
    if (status != OrderStatus.CONFIRMED) {
      throw new IllegalOrderStateTransitionException(status, OrderStatus.COMPLETED);
    }
    if (usedPoints < 0) {
      throw new InvalidOrderFeeLineException("利用ポイントは 0 以上です");
    }
    if (usedPoints > 0) {
      feeLines.add(
          OrderFeeLine.of(
              OrderFeeLineKind.POINT_REDEMPTION, POINT_REDEMPTION_LINE_NAME, -usedPoints));
      recalculateTotalFee();
    }
    this.autoGrantPoints = autoGrantPoints;
    transitionTo(OrderStatus.COMPLETED);
  }

  /**
   * 確定済みの注文を理由付きで取消す。定義域は CONFIRMED → CANCELLED のみ — 未処理の予約申請は申請側の謝絶が受け持ち、
   * 誤って完了した受注の救済経路はまだ存在しない（ADR 0013）。
   *
   * <p>二度目の取消は同一状態への静默冪等（{@link #transitionTo}）に委ねず明示的に撥ねる。通せば初回の理由と実行者が黙って上書きされ、理由を必須にした意味が消える。
   */
  public void cancelWith(String reason, Long actorId, OffsetDateTime at) {
    if (status != OrderStatus.CONFIRMED) {
      throw new IllegalOrderStateTransitionException(status, OrderStatus.CANCELLED);
    }
    if (reason == null || reason.isBlank()) {
      throw new InvalidOrderCancellationException("取消の理由は必須です");
    }
    if (actorId == null) {
      throw new InvalidOrderCancellationException("取消の実行者は必須です");
    }
    if (at == null) {
      throw new InvalidOrderCancellationException("取消の日時は必須です");
    }
    this.cancelledReason = reason;
    this.cancelledBy = actorId;
    this.cancelledAt = at;
    transitionTo(OrderStatus.CANCELLED);
  }

  /** 指定ステータスへ遷移する。同一ステータスへは冪等（何もしない）、不正な遷移はドメイン例外を投げる。 */
  public void transitionTo(OrderStatus target) {
    if (status == target) {
      return;
    }
    if (status == null || !status.canTransitionTo(target)) {
      throw new IllegalOrderStateTransitionException(status, target);
    }
    this.status = target;
  }

  @Override
  public String toString() {
    return "Order(id=" + getId() + ", businessDate=" + businessDate + ", status=" + status + ")";
  }
}
