package com.kizuna.reporting.dashboard;

import java.util.List;

/** 期間の合計と、期間の初日から末日まで欠けなく並ぶ日別。 */
public record StoreSales(SalesTotals total, List<DailySales> daily) {
  public StoreSales {
    daily = List.copyOf(daily);
  }
}
