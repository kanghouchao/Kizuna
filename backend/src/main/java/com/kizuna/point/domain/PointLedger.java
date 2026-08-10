package com.kizuna.point.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

  /** 基準日時点で利用できるポイントの合計。 */
  public int balance() {
    return lots.stream().filter(this::usable).mapToInt(PointLedger::remaining).sum();
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
