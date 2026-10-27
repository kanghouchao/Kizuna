package com.kizuna.point.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 加算ロットの集まりから残高と消費計画を導く計算器。永続化も日時取得も持たず、基準日は呼出側が渡す。
 *
 * <p>期限切れが取り去るのは残りの利用可能量だけで、そのロットへ既に引き当てられた消費は履歴として残り続ける。 だから残高は「期限内ロットの残り」の合計であって、加算量の合計ではない。
 */
public final class PointLedger {

  private final List<PointLot> lots;
  private final LocalDate today;

  public PointLedger(List<PointLot> lots, LocalDate today) {
    this.lots = List.copyOf(lots);
    this.today = today;
  }

  /** 基準日時点で利用できるポイントの合計。1 ロットの量は int でも、ロットを跨いだ合計は int を超えうるため long で持つ。 */
  public long balance() {
    return lots.stream().filter(this::usable).mapToLong(PointLedger::remaining).sum();
  }

  /**
   * 利用可能残高を期限ごとに畳んだ内訳。{@link #balance} と同じ「利用できる」判定を使うので、内訳の合計は残高に一致する。
   * 期限の早い順に並び、期限なしは末尾。残りの無い期限は現れない。
   */
  public List<ExpiryBalance> balanceByExpiry() {
    Map<LocalDate, Long> totals = new LinkedHashMap<>();
    for (PointLot lot : consumptionOrder()) {
      int rest = remaining(lot);
      if (rest > 0) {
        totals.merge(lot.expiresOn(), (long) rest, Long::sum);
      }
    }
    return totals.entrySet().stream()
        .map(entry -> new ExpiryBalance(entry.getKey(), entry.getValue()))
        .toList();
  }

  /**
   * {@code points} を消費する引き当て計画を作る。期限が早いロットから順に使い、期限なしは最後（同期限は仕訳 ID の昇順）。
   *
   * <p>利用可能量が足りなければ何も引き当てずに {@link InsufficientPointBalanceException} を投げる — 部分的な消費は作らない。
   */
  public List<PlannedAllocation> planConsumption(int points) {
    if (points <= 0) {
      throw new InvalidPointEntryException("消費ポイントは 1 以上で指定してください");
    }
    List<PlannedAllocation> plan = new ArrayList<>();
    int rest = points;
    for (PointLot lot : consumptionOrder()) {
      if (rest == 0) {
        break;
      }
      int take = Math.min(rest, remaining(lot));
      if (take == 0) {
        continue;
      }
      plan.add(new PlannedAllocation(lot.entryId(), take));
      rest -= take;
    }
    if (rest > 0) {
      throw new InsufficientPointBalanceException(balance());
    }
    return plan;
  }

  /** 受注会計での利用の引き当て計画。利用は単位の倍数に限る（手動調整の減算には単位が掛からない）。 */
  public List<PlannedAllocation> planRedemption(int points, int unit) {
    if (points <= 0 || points % unit != 0) {
      throw new InvalidPointEntryException("利用ポイントは " + unit + " ポイント単位で指定してください");
    }
    return planConsumption(points);
  }

  /**
   * 全ロットの未消費量を期限の有無に依らず引き当てる（退会消去）。期限切れの残りも含めるのは、退会後の失効批が 退会済み会員へ別の仕訳を積まないようにするため —
   * 退会消去の後、その会員の台帳に残量は無い。
   */
  public List<PlannedAllocation> planClearance() {
    return lots.stream()
        .filter(lot -> remaining(lot) > 0)
        .sorted(Comparator.comparing(PointLot::entryId))
        .map(lot -> new PlannedAllocation(lot.entryId(), remaining(lot)))
        .toList();
  }

  /** 期限切れロットの未消費量だけを記帳し、利用可能残高には触れない。 */
  public List<PlannedAllocation> planExpiry() {
    return lots.stream()
        .filter(lot -> !usable(lot) && remaining(lot) > 0)
        .sorted(Comparator.comparing(PointLot::entryId))
        .map(lot -> new PlannedAllocation(lot.entryId(), remaining(lot)))
        .toList();
  }

  /** 消費順に並べた利用可能ロット。{@link #balance} と同じ「利用できる」判定を使う。 */
  private List<PointLot> consumptionOrder() {
    return lots.stream()
        .filter(this::usable)
        .sorted(
            Comparator.comparing(
                    PointLot::expiresOn, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(PointLot::entryId))
        .toList();
  }

  /** 基準日を過ぎていないロットか（期限当日はまだ使える）。 */
  private boolean usable(PointLot lot) {
    return lot.expiresOn() == null || !lot.expiresOn().isBefore(today);
  }

  /** 引き当て済みを差し引いた残り。引き当て合計が加算量を超えた行があっても、残高を押し下げないよう 0 で止める。 */
  private static int remaining(PointLot lot) {
    return Math.max(0, lot.amount() - lot.consumed());
  }
}
