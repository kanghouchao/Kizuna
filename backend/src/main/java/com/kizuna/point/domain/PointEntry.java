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
 *   <li>増減 0 の仕訳は作れない。取りうる向きは種別が決める（{@link PointEntryType}）。有効期限を持てるのは新しいロットになる加算だけ。
 *   <li>引き当ては減算と利用取消だけが持ち、その合計は増減の絶対値に一致する。これにより加算ロットの消費済み量が引き当ての合計だけで求まる。
 *   <li>付与と利用は受注 ID、取消と利用取消は元の仕訳 ID、手動調整は理由と冪等キー、特典付与は産地の規則 ID を必ず持つ。
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

  /** 付与を産んだ特典規則。特典付与だけが設定し、この指し返しが規則の物理削除を封じる（FK RESTRICT）。 */
  @Column(name = "benefit_rule_id", updatable = false)
  private Long benefitRuleId;

  /** 打ち消す元の仕訳。取消（加算ロット）と利用取消（元の利用）だけが設定する。 */
  @Column(name = "original_entry_id", updatable = false)
  private Long originalEntryId;

  @Column(name = "actor_user_id", updatable = false)
  private Long actorUserId;

  /** 人手の操作の理由。手動調整と、巻き戻しが積む取消・利用取消が持つ。 */
  @Column(name = "reason", updatable = false, length = 500)
  private String reason;

  /** 手動調整の冪等キー（クライアント生成）。一意制約により応答喪失後の再送が二重記帳になるのを遮断する（ADR 0007）。 手動調整以外の種別は持たない。 */
  @Column(name = "idempotency_key", updatable = false, length = 64)
  private String idempotencyKey;

  /**
   * 誤帰属の訂正で、どの帰属記録を訂正したか（ADR 0012）。訂正の手動調整だけが持ち、顧客経路の通常の調整は null。
   *
   * <p>減算は期限の早いロットから引く引き当てで、誤って付与されたロットを狙い撃ちにはしないため、この指し返しが無いと台帳の側から訂正の由来を辿れない。
   *
   * <p>帰属記録は受注と共に消えうるので欠落しうる（FK は SET NULL）。台帳行はグループ資産として残る側であり、指し先の生存に巻き込まれない。
   */
  @Column(name = "corrected_attribution_id", updatable = false)
  private Long correctedAttributionId;

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
      Long benefitRuleId,
      Long originalEntryId,
      Long actorUserId,
      String reason,
      String idempotencyKey,
      Long correctedAttributionId,
      List<PointAllocation> allocations) {
    if (memberId == null) {
      throw new InvalidPointEntryException("会員 ID は必須です");
    }
    if (amount == 0) {
      throw new InvalidPointEntryException("増減が 0 の仕訳は記録できません");
    }
    validateSign(entryType, amount);
    validateExpiryAndAllocations(entryType, amount, expiresOn, allocations);
    validateReferences(entryType, orderId, benefitRuleId, originalEntryId, reason, idempotencyKey);

    this.entryType = entryType;
    this.memberId = memberId;
    this.amount = amount;
    this.expiresOn = expiresOn;
    this.originatingStoreId = originatingStoreId;
    this.orderId = orderId;
    this.benefitRuleId = benefitRuleId;
    this.originalEntryId = originalEntryId;
    this.actorUserId = actorUserId;
    this.reason = reason;
    this.idempotencyKey = idempotencyKey;
    this.correctedAttributionId = correctedAttributionId;
    this.allocations = new ArrayList<>(allocations);
  }

  private static void validateSign(PointEntryType entryType, int amount) {
    if (amount > 0 && !entryType.creditable()) {
      throw new InvalidPointEntryException("この種別は加算の仕訳にできません");
    }
    if (amount < 0 && !entryType.debitable()) {
      throw new InvalidPointEntryException("この種別は減算の仕訳にできません");
    }
  }

  /**
   * 有効期限と引き当ての規律。
   *
   * <p>利用取消は加算だが新しいロットにはならないため、期限を持たず、引き当ては元のロットへ<b>返す</b>量として持つ。 消費済み量の集計はこの向きを符号で読み分ける（{@link
   * PointAllocationRepository#findConsumedBySourceEntryIds}）。
   */
  private static void validateExpiryAndAllocations(
      PointEntryType entryType,
      int amount,
      LocalDate expiresOn,
      List<PointAllocation> allocations) {
    if (amount > 0 && entryType != PointEntryType.USE_CANCEL) {
      if (!allocations.isEmpty()) {
        throw new InvalidPointEntryException("加算の仕訳は引き当てを持ちません");
      }
      return;
    }
    if (expiresOn != null) {
      throw new InvalidPointEntryException("ロットにならない仕訳に有効期限は設定できません");
    }
    int allocated = allocations.stream().mapToInt(PointAllocation::getAmount).sum();
    if (allocated != Math.abs(amount)) {
      throw new InvalidPointEntryException("引き当ての合計が増減量と一致しません");
    }
  }

  private static void validateReferences(
      PointEntryType entryType,
      String orderId,
      Long benefitRuleId,
      Long originalEntryId,
      String reason,
      String idempotencyKey) {
    if ((entryType == PointEntryType.ORDER_GRANT || entryType == PointEntryType.USE)
        && (orderId == null || orderId.isBlank())) {
      throw new InvalidPointEntryException("受注 ID は必須です");
    }
    // 産地の規則を名乗らない特典付与は「なぜこの点数か」を辿れず、規則の物理削除も封じられない。
    // 逆に規則を名乗る他種別は、取消方法（種別から導く）の定まらない行になる。
    if ((entryType == PointEntryType.BENEFIT_GRANT) != (benefitRuleId != null)) {
      throw new InvalidPointEntryException("特典規則を名乗れるのは特典付与だけで、特典付与は必ず名乗ります");
    }
    if ((entryType == PointEntryType.CANCEL || entryType == PointEntryType.USE_CANCEL)
        && originalEntryId == null) {
      throw new InvalidPointEntryException("取消対象の仕訳 ID は必須です");
    }
    // 利用取消は受注 ID を持たない。持たせると受注ごとの付与合計（来店の獲得点）へ混ざり、
    // 受注を根拠とする加算を集める巻き戻しが自分自身を取消対象に拾う。
    if (entryType == PointEntryType.USE_CANCEL && orderId != null) {
      throw new InvalidPointEntryException("利用取消は受注 ID を持ちません");
    }
    if (entryType == PointEntryType.MANUAL_ADJUST && (reason == null || reason.isBlank())) {
      throw new InvalidPointEntryException("手動調整の理由は必須です");
    }
    // 取消・利用取消はどちらも人手の巻き戻しが積む。理由が無いと、残高が減った説明が台帳の外にしか無くなる。
    if ((entryType == PointEntryType.CANCEL || entryType == PointEntryType.USE_CANCEL)
        && (reason == null || reason.isBlank())) {
      throw new InvalidPointEntryException("取消の理由は必須です");
    }
    if (entryType == PointEntryType.MANUAL_ADJUST
        && (idempotencyKey == null || idempotencyKey.isBlank())) {
      throw new InvalidPointEntryException("手動調整の冪等キーは必須です");
    }
  }

  /**
   * 受注完了に伴う付与。
   *
   * <p>有効期限は付けない。通常の付与は無期限であり、期限を持つのは特典と手動調整の産だけである。
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
        null,
        actorUserId,
        null,
        null,
        null,
        List.of());
  }

  /**
   * 特典規則が産んだ付与。有効期限は規則の「付与ポイント有効期間」から呼出側が算出して渡す（無期限なら null）。
   *
   * <p>受注 ID を必須にするのは、投産済みの種別が受注を条件とするものだけだからである。受注を名乗らなければ、
   * 受注から辿る巻き戻しがこの行を永久に見つけられない。ログイン条件の特典を投産するときは、この要求と {@code PointEntryTypeTest}
   * の枚挙を同時に見直すこと（枚挙が先に赤くなる）。
   */
  public static PointEntry grantForBenefit(
      Long memberId,
      String orderId,
      Long storeId,
      int amount,
      LocalDate expiresOn,
      Long benefitRuleId,
      Long actorUserId) {
    if (amount <= 0) {
      throw new InvalidPointEntryException("特典の付与ポイントは 1 以上で指定してください");
    }
    if (orderId == null || orderId.isBlank()) {
      throw new InvalidPointEntryException("受注 ID は必須です");
    }
    return new PointEntry(
        PointEntryType.BENEFIT_GRANT,
        memberId,
        amount,
        expiresOn,
        storeId,
        orderId,
        benefitRuleId,
        null,
        actorUserId,
        null,
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
        null,
        actorUserId,
        null,
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
        null,
        actorUserId,
        reason,
        idempotencyKey,
        null,
        allocations);
  }

  /**
   * 誤帰属の訂正（ADR 0012）。種別は手動調整そのもので、宛先が帰属記録の持つ会員であることと、どの帰属記録を訂正したかを 指し返すことだけが通常の調整と違う。
   *
   * <p>減算は期限の早いロットから引く引き当てであり、誤って付与されたロットを狙い撃ちにはしない。狙い撃ちにしないからこそ、
   * そのロットへ既に引き当て済みの消費行へ手を触れずに済み、「引き当ての合計は減算量に一致する」が破れない。
   *
   * <p>有効期限は持たない。訂正は残高を引く操作であって加算ロットを作らないため、期限を載せる先が無い。
   */
  public static PointEntry correctAttribution(
      Long memberId,
      Long storeId,
      int delta,
      String reason,
      List<PointAllocation> allocations,
      Long actorUserId,
      String idempotencyKey,
      Long correctedAttributionId) {
    if (correctedAttributionId == null) {
      throw new InvalidPointEntryException("訂正した帰属記録は必須です");
    }
    return new PointEntry(
        PointEntryType.MANUAL_ADJUST,
        memberId,
        delta,
        null,
        storeId,
        null,
        null,
        null,
        actorUserId,
        reason,
        idempotencyKey,
        correctedAttributionId,
        allocations);
  }

  /**
   * 加算仕訳の取消。未消費分 {@code available} だけを打ち消し、元の行は書き換えない。
   *
   * <p>{@code available} は台帳全体の引き当て合計からしか求まらないため呼出側が渡す。理由は打ち消しを起こした 操作のものを引き継ぐ —
   * 台帳の行だけを見て「なぜ消えたか」が辿れないと、残高の差の説明が台帳の外にしか無くなる。
   */
  public static PointEntry cancel(
      PointEntry original, int available, String reason, Long actorUserId) {
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
        null,
        original.getId(),
        actorUserId,
        reason,
        null,
        null,
        List.of(PointAllocation.of(original.getId(), available)));
  }

  /**
   * 利用の逆転（巻き戻し）。元の利用が引き当てた量を、そのまま<b>元のロットへ返す</b>。
   *
   * <p>返すのは引き当ての鏡像なので、期限は元のロットのものが保たれる。返った残量が既に期限切れなら翌日の失効批が拾う — 期限を保つことの自然な帰結であり、意図した挙動である。
   *
   * <p>1 件の利用への逆転は高々 1 件。二度目は元の仕訳への参照の一意性が撥ねる。
   */
  public static PointEntry reverseUse(PointEntry originalUse, String reason, Long actorUserId) {
    if (originalUse.getId() == null) {
      throw new InvalidPointEntryException("取消対象の仕訳 ID は必須です");
    }
    if (originalUse.getEntryType() != PointEntryType.USE) {
      throw new InvalidPointEntryException("逆転できるのは利用の仕訳だけです");
    }
    return new PointEntry(
        PointEntryType.USE_CANCEL,
        originalUse.getMemberId(),
        -originalUse.getAmount(),
        null,
        originalUse.getOriginatingStoreId(),
        null,
        null,
        originalUse.getId(),
        actorUserId,
        reason,
        null,
        null,
        originalUse.getAllocations().stream()
            .map(
                allocation ->
                    PointAllocation.of(allocation.getSourceEntryId(), allocation.getAmount()))
            .toList());
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
        null,
        actorUserId,
        null,
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
