package com.kizuna.order.domain;

import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
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

  @Embedded private OrderCourse course;

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
  @Column(name = "total_fee", nullable = false)
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

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "order_id", nullable = false)
  @OrderBy("id")
  @Getter(AccessLevel.NONE)
  @Builder.Default
  private List<OrderSpecialService> specialServiceLines = new ArrayList<>();

  private OffsetDateTime startedAt;

  private OffsetDateTime completedAt;

  @Column(nullable = false)
  private int accruedRemuneration;

  private Long startedBy;
  private String startReason;

  public List<SpecialServiceSnapshot> getSpecialServices() {
    return specialServiceLines.stream().map(OrderSpecialService::getSnapshot).toList();
  }

  public void adoptServices(
      OrderCourse course, List<SpecialServiceSnapshot> services, List<OrderFeeLineDraft> drafts) {
    if (status.isTerminal()) throw new InvalidOrderFeeLineException("終端の内容は専用訂正で変更してください");
    replaceSpecialServices(services);
    adoptCourse(course, drafts);
  }

  private void replaceSpecialServices(List<SpecialServiceSnapshot> services) {
    if (services.stream().map(SpecialServiceSnapshot::serviceId).distinct().count()
        != services.size()) throw new InvalidOrderFeeLineException("同じ特殊サービスは一度だけ選択できます");
    var ids = services.stream().map(SpecialServiceSnapshot::serviceId).toList();
    specialServiceLines.removeIf(line -> !ids.contains(line.getSnapshot().serviceId()));
    for (var snapshot : services) {
      var existing =
          specialServiceLines.stream()
              .filter(line -> line.getSnapshot().serviceId().equals(snapshot.serviceId()))
              .findFirst();
      if (existing.isPresent()) existing.get().adopt(snapshot);
      else specialServiceLines.add(OrderSpecialService.of(snapshot));
    }
  }

  public void correctServices(
      List<SpecialServiceSnapshot> services, OrderCorrectionCommand command) {
    if (status != OrderStatus.COMPLETED)
      throw new InvalidOrderCorrectionException("完了した受注だけが訂正できます");
    replaceSpecialServices(services);
    correct(command);
  }

  public void start(String reason, Long actorId, OffsetDateTime at) {
    if (status != OrderStatus.CONFIRMED)
      throw new IllegalOrderStateTransitionException(status, OrderStatus.IN_SERVICE);
    if (reason == null
        || reason.isBlank()
        || reason.length() > 500
        || actorId == null
        || at == null) throw new InvalidOrderFeeLineException("開始の理由・実行者・日時は必須です");
    startedAt = at;
    startedBy = actorId;
    startReason = reason;
    transitionTo(OrderStatus.IN_SERVICE);
  }

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
   * 編集可能明細を置き換える。維持指定の行は同一性と採用条件を保持する。
   *
   * <p>システム専有の行（ポイント利用）は要求に含められず、既にある行はこの経路で消えない。台帳の減算仕訳と対で書かれた記録が 通常の編集で外れると、内訳と台帳が黙って食い違う。
   *
   * <p>基本コース料金の行名称は受注のコース名の写しから採る。行の側にも名前を名乗らせると、同じ受注が二つのコース名を主張する。
   */
  public void replaceStoreFeeLines(List<OrderFeeLineDraft> drafts) {
    // 列へ畳む前の long で判定する（畳みを long にする理由は recalculateTotalFee）
    if (swapStoreFeeLines(drafts) < 0) {
      throw new InvalidOrderFeeLineException("内訳の総和が負になっています。割引・調整の金額を見直してください");
    }
  }

  /** コースだけの変更では、他の編集可能明細とポイント利用行を保持する。 */
  public void adoptCourse(OrderCourse next, List<OrderFeeLineDraft> drafts) {
    if (status == null || status.isTerminal()) {
      throw new InvalidOrderFeeLineException("終端のコースは専用訂正で変更してください");
    }
    this.course = next;
    replaceStoreFeeLines(drafts == null ? editableFeeLines() : drafts);
  }

  public List<OrderFeeLineDraft> editableFeeLines() {
    return feeLines.stream()
        .filter(
            line ->
                !line.getKind().isSystemOwned()
                    && line.getKind() != OrderFeeLineKind.BASE_COURSE
                    && line.getKind() != OrderFeeLineKind.SPECIAL_SERVICE)
        .map(OrderFeeLineDraft::of)
        .toList();
  }

  private long swapStoreFeeLines(List<OrderFeeLineDraft> drafts) {
    if (course == null) throw new InvalidOrderFeeLineException("コースを選択してください");
    List<OrderFeeLine> replaced = new ArrayList<>();
    var ids = new HashSet<String>();
    var services = new HashSet<String>();
    for (OrderFeeLineDraft draft : drafts) {
      if (draft.kind() == OrderFeeLineKind.SPECIAL_SERVICE
          || draft.kind() == OrderFeeLineKind.BASE_COURSE
          || (draft.kind() != null && draft.kind().isSystemOwned())) {
        throw new InvalidOrderFeeLineException("コースとポイントの明細は直接変更できません");
      }
      OrderFeeLine line;
      if (draft.lineId() == null) {
        line = OrderFeeLine.from(draft);
        if (draft.kind() == OrderFeeLineKind.SURCHARGE) {
          var existing =
              feeLines.stream()
                  .filter(
                      item ->
                          item.getKind() == OrderFeeLineKind.SURCHARGE
                              && item.getAdoption()
                                  .serviceId()
                                  .equals(draft.adoption().serviceId()))
                  .findFirst()
                  .orElse(null);
          if (existing != null) {
            existing.reselectSurcharge(draft);
            line = existing;
          }
        }
      } else {
        if (!ids.add(draft.lineId())) throw new InvalidOrderFeeLineException("明細が重複しています");
        line =
            feeLines.stream()
                .filter(item -> draft.lineId().equals(item.getId()))
                .findFirst()
                .orElse(null);
        if (line == null) {
          line = OrderFeeLine.from(draft);
          line.setId(draft.lineId());
        }
      }
      if (line.getKind() == OrderFeeLineKind.SURCHARGE
          && !services.add(line.getAdoption().serviceId()))
        throw new InvalidOrderFeeLineException("同じ加算は一度だけ選択できます");
      line.attachStore(getStoreId());
      replaced.add(line);
    }
    var base =
        feeLines.stream()
            .filter(line -> line.getKind() == OrderFeeLineKind.BASE_COURSE)
            .findFirst()
            .orElse(null);
    if (base == null) {
      base = OrderFeeLine.course(course);
      base.attachStore(getStoreId());
      feeLines.add(base);
    } else {
      base.applyCourse(course);
    }
    feeLines.removeIf(
        line ->
            !line.getKind().isSystemOwned()
                && line.getKind() != OrderFeeLineKind.BASE_COURSE
                && line.getKind() != OrderFeeLineKind.SPECIAL_SERVICE
                && !replaced.contains(line));
    var selected = getSpecialServices();
    feeLines.removeIf(
        line ->
            line.getKind() == OrderFeeLineKind.SPECIAL_SERVICE
                && selected.stream()
                    .noneMatch(item -> item.serviceId().equals(line.getServiceId())));
    for (var item : selected) {
      var existing =
          feeLines.stream()
              .filter(line -> item.serviceId().equals(line.getServiceId()))
              .findFirst();
      if (existing.isPresent()) existing.get().applySpecialService(item);
      else feeLines.add(OrderFeeLine.specialService(item));
    }
    for (var line : replaced) if (!feeLines.contains(line)) feeLines.add(line);
    return recalculateTotalFee();
  }

  @PrePersist
  void attachLineStores() {
    feeLines.forEach(line -> line.attachStore(getStoreId()));
  }

  public int getTotalRemuneration() {
    return checkedTotal(feeLines.stream().mapToLong(OrderFeeLine::getRemuneration).sum());
  }

  public int getTotalDurationMinutes() {
    return checkedTotal(
        (course == null ? 0L : course.durationMinutes())
            + (extensionMinutes == null ? 0L : extensionMinutes));
  }

  private static int checkedTotal(long value) {
    if (value < 0 || value > Integer.MAX_VALUE)
      throw new InvalidOrderFeeLineException("合計が扱える上限を超えています");
    return (int) value;
  }

  /**
   * 自動付与の基準になる金額 — ポイント利用を除いた明細の総和。
   *
   * <p>{@link #totalFee} はポイント利用の減算も含む「控除後の請求額」なので、そのまま基準にすると同じ会計が ポイントを使うほど付与も減る。付与の基準はこちらである（ADR
   * 0018）。
   */
  public int grantBasisAmount() {
    return feeLines.stream()
        .filter(line -> !line.getKind().isSystemOwned())
        .mapToInt(OrderFeeLine::getAmount)
        .sum();
  }

  /**
   * 合計を明細から取り直す。行を動かす経路はすべてここを通り、和と合計が食い違う状態を集約の外から作れないようにする。
   *
   * <p>積むのは long。int で畳むと 32 ビットの巻き戻りが起き、上限を超えた総和も列に収まらない負の総和も
   * 別の値に化けて呼出側の不変量を素通りする。列へ写すのは範囲に収まると決まった後だけで、 収まらない負は long
   * のまま返して呼出側の文言で撥ねさせる（拒否の巻き戻しはトランザクションが担う）。
   *
   * @return 取り直した総和（列へ畳む前の値）
   */
  private long recalculateTotalFee() {
    this.extensionMinutes =
        checkedTotal(
            feeLines.stream()
                .filter(line -> line.getKind() == OrderFeeLineKind.EXTENSION)
                .mapToLong(OrderFeeLine::getDurationMinutes)
                .sum());
    getTotalDurationMinutes();
    if (status == OrderStatus.COMPLETED) accruedRemuneration = getTotalRemuneration();
    else getTotalRemuneration();
    checkedTotal(
        feeLines.stream()
            .filter(
                line ->
                    !line.getKind().isDeduction()
                        && line.getKind() != OrderFeeLineKind.POINT_REDEMPTION_OFFSET)
            .mapToLong(OrderFeeLine::getAmount)
            .sum());
    long sum = feeLines.stream().mapToLong(OrderFeeLine::getAmount).sum();
    if (sum > Integer.MAX_VALUE) {
      throw new InvalidOrderFeeLineException("内訳の総和が扱える上限を超えています。金額を見直してください");
    }
    if (sum >= Integer.MIN_VALUE) {
      this.totalFee = (int) sum;
    }
    return sum;
  }

  /** 台帳が返した利用だけを相殺し、元利用と固定報酬を保持する。 */
  public void offsetPointRedemption(long restoredPoints) {
    if (status != OrderStatus.COMPLETED
        || feeLines.stream()
            .anyMatch(line -> line.getKind() == OrderFeeLineKind.POINT_REDEMPTION_OFFSET)) {
      throw new InvalidOrderFeeLineException("完了した受注の利用だけを一度相殺できます");
    }
    long used =
        -feeLines.stream()
            .filter(line -> line.getKind() == OrderFeeLineKind.POINT_REDEMPTION)
            .mapToLong(OrderFeeLine::getAmount)
            .sum();
    if (restoredPoints != used || restoredPoints < 0) {
      throw new InvalidOrderFeeLineException("台帳の返還量と受注の利用額が一致しません");
    }
    if (restoredPoints == 0) return;
    checkedTotal((long) totalFee + restoredPoints);
    var offset =
        OrderFeeLine.of(
            OrderFeeLineKind.POINT_REDEMPTION_OFFSET, "ポイント利用取消", checkedTotal(restoredPoints));
    offset.attachStore(getStoreId());
    feeLines.add(offset);
    recalculateTotalFee();
  }

  /**
   * 会計を確定して未完了（CONFIRMED / IN_SERVICE）の受注を完了する。ポイント利用の明細と自動付与ポイントはこの経路でのみ確定する。
   *
   * <p>会計金額は引数で受けない — 合計は明細の総和として既に決まっている。利用ポイントは減算の明細行として内訳へ入り、
   * 合計はそのぶん下がる（ポイント控除後の請求額が合計になる）。付与の基準と利用の上限は、この行が入る前の総和を呼出側が読んで決める。
   *
   * <p>同一状態への静默冪等（{@link #transitionTo}）に委ねず、完了済みを明示的に撥ねる。完了は台帳記帳と不可分のため、
   * 二度目の呼出を黙って通すと同じ受注で付与・利用が二重に記帳される。
   */
  public void completeWith(int usedPoints, int autoGrantPoints) {
    if (status == null || status.isTerminal()) {
      throw new IllegalOrderStateTransitionException(status, OrderStatus.COMPLETED);
    }
    if (usedPoints < 0) {
      throw new InvalidOrderFeeLineException("利用ポイントは 0 以上です");
    }
    if (usedPoints > 0) {
      var redemption =
          OrderFeeLine.of(
              OrderFeeLineKind.POINT_REDEMPTION, POINT_REDEMPTION_LINE_NAME, -usedPoints);
      redemption.attachStore(getStoreId());
      feeLines.add(redemption);
      if (recalculateTotalFee() < 0) {
        throw new InvalidOrderFeeLineException("内訳の総和が負になっています。割引・調整の金額を見直してください");
      }
    }
    this.autoGrantPoints = autoGrantPoints;
    transitionTo(OrderStatus.COMPLETED);
    completedAt = OffsetDateTime.now();
    accruedRemuneration = getTotalRemuneration();
  }

  /** 完了済みの内容だけを訂正し、状態・完了時の付与・ポイント利用を保持する。 コースを先に適用し、基本料金の名称・金額を同じ快照から導出する。 */
  public void correct(OrderCorrectionCommand command) {
    if (status != OrderStatus.COMPLETED) {
      throw new InvalidOrderCorrectionException("完了した受注だけが訂正できます");
    }
    this.actualArrivalTime = command.actualArrivalTime();
    this.actualEndTime = command.actualEndTime();
    if (command.course() != null) this.course = command.course();
    // 総和が 0 以上という不変量は他の経路と同じだが、門は利用の行を動かせないため、下回った差を
    // 吸収する先が無いことまで伝える固有の文言を持つ（一般の差し替えは割引・調整を直せばよい）。
    // 撥ねた訂正の巻き戻しはトランザクションが担う（この時点で明細は既に差し替わっている）。
    if (swapStoreFeeLines(command.feeLines()) < 0) {
      throw new InvalidOrderFeeLineException("訂正後の請求額が利用ポイントを下回ります。ポイント利用の訂正はポイント機構で行ってください");
    }
  }

  /**
   * 未完了（CONFIRMED / IN_SERVICE）の受注を理由付きで取消す。未処理の予約申請は申請側の謝絶が受け持ち、 完了した受注は状態を戻さず内容だけを訂正する（{@link
   * #correct}。ADR 0019）。
   *
   * <p>二度目の取消は同一状態への静默冪等（{@link #transitionTo}）に委ねず明示的に撥ねる。通せば初回の理由と実行者が黙って上書きされ、理由を必須にした意味が消える。
   */
  public void cancelWith(String reason, Long actorId, OffsetDateTime at) {
    if (status == null || status.isTerminal()) {
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
