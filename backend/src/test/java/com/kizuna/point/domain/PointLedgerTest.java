package com.kizuna.point.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PointLedgerTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 8, 10);

  @Test
  @DisplayName("残高は期限内ロットの残りの合計であること")
  void balanceSumsRemainingOfLiveLots() {
    PointLedger ledger =
        ledger(
            new PointLot(1L, 500, null, 200), new PointLot(2L, 300, LocalDate.of(2026, 9, 30), 0));

    assertThat(ledger.balance()).isEqualTo(600);
  }

  @Test
  @DisplayName("期限当日のロットはまだ使えること")
  void lotExpiringTodayIsStillUsable() {
    assertThat(ledger(new PointLot(1L, 100, TODAY, 0)).balance()).isEqualTo(100);
    assertThat(ledger(new PointLot(1L, 100, TODAY.minusDays(1), 0)).balance()).isZero();
  }

  @Test
  @DisplayName("期限切れが取り去るのは残りだけで、既に引き当てられた消費は戻らないこと")
  void expiryRemovesOnlyRemainingAvailability() {
    PointLot expiredPartlyUsed = new PointLot(1L, 500, LocalDate.of(2026, 8, 9), 200);
    PointLot expiredUntouched = new PointLot(1L, 500, LocalDate.of(2026, 8, 9), 0);
    PointLot live = new PointLot(2L, 300, null, 0);

    // 期限切れロットは消費済みの多寡に関わらず残高へ寄与しない（消費済み分が残高へ戻ることもない）。
    assertThat(ledger(expiredPartlyUsed, live).balance()).isEqualTo(300);
    assertThat(ledger(expiredUntouched, live).balance()).isEqualTo(300);
  }

  @Test
  @DisplayName("消費は期限の早い順・期限なしは最後・同期限は仕訳 ID 昇順で引き当てること")
  void planConsumptionFollowsExpiryThenIdOrder() {
    PointLedger ledger =
        ledger(
            new PointLot(3L, 100, LocalDate.of(2026, 9, 1), 0),
            new PointLot(1L, 100, null, 0),
            new PointLot(2L, 100, LocalDate.of(2026, 9, 1), 0),
            new PointLot(4L, 100, LocalDate.of(2026, 8, 15), 0));

    List<PlannedAllocation> plan = ledger.planConsumption(400);

    assertThat(plan).extracting(PlannedAllocation::sourceEntryId).containsExactly(4L, 2L, 3L, 1L);
  }

  @Test
  @DisplayName("複数ロットを跨いで過不足なく引き当てること")
  void planConsumptionDrainsExactlyAcrossLots() {
    PointLedger ledger =
        ledger(
            new PointLot(1L, 100, LocalDate.of(2026, 8, 20), 0),
            new PointLot(2L, 100, LocalDate.of(2026, 8, 25), 0),
            new PointLot(3L, 100, null, 0));

    List<PlannedAllocation> plan = ledger.planConsumption(250);

    assertThat(plan)
        .extracting(PlannedAllocation::sourceEntryId, PlannedAllocation::amount)
        .containsExactly(tuple(1L, 100), tuple(2L, 100), tuple(3L, 50));
  }

  @Test
  @DisplayName("残りの一部しか賄えないときは何も引き当てず残高付きで拒否すること")
  void planConsumptionRejectsWhenInsufficient() {
    PointLedger ledger = ledger(new PointLot(1L, 200, null, 50), new PointLot(2L, 200, null, 50));

    assertThatThrownBy(() -> ledger.planConsumption(301))
        .isInstanceOf(InsufficientPointBalanceException.class)
        .hasMessageContaining("残高: 300");
  }

  @Test
  @DisplayName("引き当て可能かの判定が残高と一致すること（残高ちょうどは通り、1 超は拒否される）")
  void planConsumptionUsesTheSameAvailabilityAsBalance() {
    PointLedger ledger =
        ledger(
            new PointLot(1L, 200, LocalDate.of(2026, 8, 9), 0),
            new PointLot(2L, 150, LocalDate.of(2026, 8, 20), 0));

    assertThat(ledger.balance()).isEqualTo(150);
    assertThat(ledger.planConsumption(150))
        .extracting(PlannedAllocation::sourceEntryId)
        .containsExactly(2L);
    assertThatThrownBy(() -> ledger.planConsumption(151))
        .isInstanceOf(InsufficientPointBalanceException.class);
  }

  @Test
  @DisplayName("0 以下の消費は計画できないこと")
  void planConsumptionRejectsNonPositive() {
    assertThatThrownBy(() -> ledger(new PointLot(1L, 100, null, 0)).planConsumption(0))
        .isInstanceOf(InvalidPointEntryException.class)
        .hasMessageContaining("消費ポイントは 1 以上");
  }

  @Test
  @DisplayName("ロットが 1 件も無ければ残高 0 で、消費は拒否されること")
  void emptyLedgerHasNoBalance() {
    PointLedger ledger = new PointLedger(List.of(), TODAY);

    assertThat(ledger.balance()).isZero();
    assertThatThrownBy(() -> ledger.planConsumption(1))
        .isInstanceOf(InsufficientPointBalanceException.class)
        .hasMessageContaining("残高: 0");
  }

  @Test
  @DisplayName("付与→利用→加算調整→取消→期限切れ の各段階で残高が積み上がること")
  void balanceIsReconstructedAtEachLedgerStep() {
    // 付与 500（期限なし）と、8/9 に切れる 50 の古いロット。基準日 8/5 では両方が生きている。
    PointLot grant = new PointLot(1L, 500, null, 0);
    PointLot old = new PointLot(3L, 50, LocalDate.of(2026, 8, 9), 0);
    LocalDate beforeExpiry = LocalDate.of(2026, 8, 5);

    assertThat(new PointLedger(List.of(grant, old), beforeExpiry).balance()).isEqualTo(550);

    // 利用 200 → 付与ロットの消費済みが 200 になる。
    PointLot used = new PointLot(1L, 500, null, 200);
    assertThat(new PointLedger(List.of(used, old), beforeExpiry).balance()).isEqualTo(350);

    // 加算調整 +100（8/31 期限）。
    PointLot adjusted = new PointLot(2L, 100, LocalDate.of(2026, 8, 31), 0);
    assertThat(new PointLedger(List.of(used, adjusted, old), beforeExpiry).balance())
        .isEqualTo(450);

    // 調整の取消は未消費分 100 を引き当てるので、そのロットの残りが 0 になる。
    PointLot cancelled = new PointLot(2L, 100, LocalDate.of(2026, 8, 31), 100);
    assertThat(new PointLedger(List.of(used, cancelled, old), beforeExpiry).balance())
        .isEqualTo(350);

    // 基準日が 8/10 になると 8/9 期限のロットが落ちる。
    assertThat(new PointLedger(List.of(used, cancelled, old), TODAY).balance()).isEqualTo(300);
  }

  private static PointLedger ledger(PointLot... lots) {
    return new PointLedger(List.of(lots), TODAY);
  }
}
