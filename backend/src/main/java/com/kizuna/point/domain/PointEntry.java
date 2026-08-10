package com.kizuna.point.domain;

import com.kizuna.shared.persistence.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ポイント台帳の 1 仕訳。追加型の台帳で、訂正も取消も新しい行として積み、既存行は書き換えない。
 *
 * <p>残高は行の合計であってどこにも保持しない。会員はプラットフォーム級の身分なので、台帳も店舗で分割しない — {@code originatingStoreId}
 * は「どの店舗で起きたか」の帰属情報にすぎず、残高の作用域ではない（列名を {@code store_id} にしないのはこのため）。
 *
 * <p>不変条件（構築時に検証、違反は 400 系ドメイン例外 {@link InvalidPointEntryException}）:
 *
 * <ol>
 *   <li>増減 0 の仕訳は作れない。加算になりうるのは付与と手動調整だけで、残りの種別は必ず減算。有効期限を持てるのは加算だけ。
 *   <li>減算は必ず引き当てを伴い、引き当ての合計は減算量に一致する。これにより加算ロットの消費済み量が引き当ての合計だけで求まる。
 *   <li>付与と利用は受注 ID、取消は取消対象の仕訳 ID、手動調整は理由と冪等キーを必ず持つ。
 * </ol>
 */
@Entity
@Table(name = "t_point_entries")
@Getter
@NoArgsConstructor
public class PointEntry extends BaseEntity {

  @Column(name = "member_id", nullable = false, updatable = false)
  private Long memberId;

  @Enumerated(EnumType.STRING)
  @Column(name = "entry_type", nullable = false, updatable = false, length = 30)
  private PointEntryType entryType;

  /** 符号付きの増減。加算は正、減算は負で、0 にはならない。 */
  @Column(name = "amount", nullable = false, updatable = false)
  private Integer amount;

  /** 加算ロットの有効期限。期限なしは null。減算は常に null。 */
  @Column(name = "expires_on", updatable = false)
  private LocalDate expiresOn;

  /** 発生店舗。残高の作用域ではなく帰属情報で、店舗を跨いだ利用も成立する。 */
  @Column(name = "originating_store_id", updatable = false)
  private Long originatingStoreId;

  @Column(name = "order_id", updatable = false, length = 64)
  private String orderId;

  /** 取消対象の加算仕訳。CANCEL のみ設定される。 */
  @Column(name = "original_entry_id", updatable = false)
  private Long originalEntryId;

  @Column(name = "actor_user_id", updatable = false)
  private Long actorUserId;

  @Column(name = "reason", updatable = false, length = 500)
  private String reason;

  /** 手動調整の冪等キー（クライアント生成）。一意制約により応答喪失後の再送が二重記帳になるのを遮断する（ADR 0007）。 手動調整以外の種別は持たない。 */
  @Column(name = "idempotency_key", updatable = false, length = 64)
  private String idempotencyKey;

  /**
   * この減算がどの加算ロットを消費したか。減算仕訳集約の一部として一緒に永続化される。
   *
   * <p>{@code nullable = false} は子側 INSERT に entry_id を含めさせるための指定で、これが無いと NULL で挿入してから UPDATE
   * する経路になり NOT NULL 制約に触れる。
   */
  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "entry_id", nullable = false)
  private List<PointAllocation> allocations = new ArrayList<>();

  private PointEntry(
      PointEntryType entryType,
      Long memberId,
      int amount,
      LocalDate expiresOn,
      Long originatingStoreId,
      String orderId,
      Long originalEntryId,
      Long actorUserId,
      String reason,
      String idempotencyKey,
      List<PointAllocation> allocations) {
    if (memberId == null) {
      throw new InvalidPointEntryException("会員 ID は必須です");
    }
    if (amount == 0) {
      throw new InvalidPointEntryException("増減が 0 の仕訳は記録できません");
    }
    validateSign(entryType, amount);
    validateExpiryAndAllocations(amount, expiresOn, allocations);
    validateReferences(entryType, orderId, originalEntryId, reason, idempotencyKey);

    this.entryType = entryType;
    this.memberId = memberId;
    this.amount = amount;
    this.expiresOn = expiresOn;
    this.originatingStoreId = originatingStoreId;
    this.orderId = orderId;
    this.originalEntryId = originalEntryId;
    this.actorUserId = actorUserId;
    this.reason = reason;
    this.idempotencyKey = idempotencyKey;
    this.allocations = new ArrayList<>(allocations);
  }

  private static void validateSign(PointEntryType entryType, int amount) {
    boolean creditable =
        entryType == PointEntryType.ORDER_GRANT || entryType == PointEntryType.MANUAL_ADJUST;
    if (amount > 0 && !creditable) {
      throw new InvalidPointEntryException("この種別は加算の仕訳にできません");
    }
    if (amount < 0 && entryType == PointEntryType.ORDER_GRANT) {
      throw new InvalidPointEntryException("付与は加算の仕訳でなければなりません");
    }
  }

  private static void validateExpiryAndAllocations(
      int amount, LocalDate expiresOn, List<PointAllocation> allocations) {
    if (amount > 0) {
      if (!allocations.isEmpty()) {
        throw new InvalidPointEntryException("加算の仕訳は引き当てを持ちません");
      }
      return;
    }
    if (expiresOn != null) {
      throw new InvalidPointEntryException("減算の仕訳に有効期限は設定できません");
    }
    int allocated = allocations.stream().mapToInt(PointAllocation::getAmount).sum();
    if (allocated != -amount) {
      throw new InvalidPointEntryException("引き当ての合計が減算量と一致しません");
    }
  }

  private static void validateReferences(
      PointEntryType entryType,
      String orderId,
      Long originalEntryId,
      String reason,
      String idempotencyKey) {
    if ((entryType == PointEntryType.ORDER_GRANT || entryType == PointEntryType.USE)
        && (orderId == null || orderId.isBlank())) {
      throw new InvalidPointEntryException("受注 ID は必須です");
    }
    if (entryType == PointEntryType.CANCEL && originalEntryId == null) {
      throw new InvalidPointEntryException("取消対象の仕訳 ID は必須です");
    }
    if (entryType == PointEntryType.MANUAL_ADJUST && (reason == null || reason.isBlank())) {
      throw new InvalidPointEntryException("手動調整の理由は必須です");
    }
    if (entryType == PointEntryType.MANUAL_ADJUST
        && (idempotencyKey == null || idempotencyKey.isBlank())) {
      throw new InvalidPointEntryException("手動調整の冪等キーは必須です");
    }
  }

  /**
   * 受注完了に伴う付与。
   *
   * <p>有効期限は付けない — 期限規則の値が未確定のため、列と検証だけ先に用意して運用開始まで無期限で積む。
   */
  public static PointEntry grantForOrder(
      Long memberId, String orderId, Long storeId, int amount, Long actorUserId) {
    if (amount <= 0) {
      throw new InvalidPointEntryException("付与ポイントは 1 以上で指定してください");
    }
    return new PointEntry(
        PointEntryType.ORDER_GRANT,
        memberId,
        amount,
        null,
        storeId,
        orderId,
        null,
        actorUserId,
        null,
        null,
        List.of());
  }

  /** 受注会計でのポイント利用。{@code points} は正の消費量で、引き当ての合計と一致していなければならない。 */
  public static PointEntry useForOrder(
      Long memberId,
      String orderId,
      Long storeId,
      int points,
      List<PointAllocation> allocations,
      Long actorUserId) {
    if (points <= 0) {
      throw new InvalidPointEntryException("利用ポイントは 1 以上で指定してください");
    }
    return new PointEntry(
        PointEntryType.USE,
        memberId,
        -points,
        null,
        storeId,
        orderId,
        null,
        actorUserId,
        null,
        null,
        allocations);
  }

  /**
   * 運用者による手動調整。{@code delta} は符号付きで、加算なら有効期限を付けられ、減算なら引き当てが要る。
   *
   * <p>冪等キーは必須。応答喪失後の再送が二重記帳になるのを一意制約で遮断する（ADR 0007）。
   */
  public static PointEntry manualAdjust(
      Long memberId,
      Long storeId,
      int delta,
      String reason,
      LocalDate expiresOn,
      List<PointAllocation> allocations,
      Long actorUserId,
      String idempotencyKey) {
    return new PointEntry(
        PointEntryType.MANUAL_ADJUST,
        memberId,
        delta,
        expiresOn,
        storeId,
        null,
        null,
        actorUserId,
        reason,
        idempotencyKey,
        allocations);
  }

  /**
   * 加算仕訳の取消。未消費分 {@code available} だけを打ち消し、元の行は書き換えない。
   *
   * <p>{@code available} は台帳全体の引き当て合計からしか求まらないため呼出側が渡す。
   */
  public static PointEntry cancel(PointEntry original, int available, Long actorUserId) {
    if (original.getId() == null) {
      throw new InvalidPointEntryException("取消対象の仕訳 ID は必須です");
    }
    if (original.getAmount() <= 0) {
      throw new InvalidPointEntryException("取り消せるのは加算の仕訳だけです");
    }
    if (available <= 0) {
      throw new InvalidPointEntryException("この仕訳は既に消費済みで取り消せません");
    }
    return new PointEntry(
        PointEntryType.CANCEL,
        original.getMemberId(),
        -available,
        null,
        original.getOriginatingStoreId(),
        null,
        original.getId(),
        actorUserId,
        null,
        null,
        List.of(PointAllocation.of(original.getId(), available)));
  }

  /**
   * 有効期限切れによる失効。実行者を持たない（機構が起こす仕訳）。
   *
   * <p>発生店舗も持たない — 失効は複数店舗発生の lot に跨る系統イベントのため単一の発生店舗を持たない。店舗帰属は充当行から源 lot を辿って照会する（取消は打ち消す対象が 1
   * つなので元の仕訳の発生店舗を引き継ぐ）。
   */
  public static PointEntry expire(Long memberId, int points, List<PointAllocation> allocations) {
    return new PointEntry(
        PointEntryType.EXPIRE,
        memberId,
        -points,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        allocations);
  }

  /** 退会に伴う残高消去。 */
  public static PointEntry withdrawalClear(
      Long memberId, int points, List<PointAllocation> allocations, Long actorUserId) {
    return new PointEntry(
        PointEntryType.WITHDRAWAL_CLEAR,
        memberId,
        -points,
        null,
        null,
        null,
        null,
        actorUserId,
        null,
        null,
        allocations);
  }

  /** 引き当ては集約の不変条件（合計＝減算量）を成すため、外から差し替えられない読み取り専用の眺めで返す。 */
  public List<PointAllocation> getAllocations() {
    return Collections.unmodifiableList(allocations);
  }

  @Override
  public String toString() {
    return "PointEntry(id="
        + getId()
        + ", memberId="
        + memberId
        + ", entryType="
        + entryType
        + ", amount="
        + amount
        + ")";
  }
}
