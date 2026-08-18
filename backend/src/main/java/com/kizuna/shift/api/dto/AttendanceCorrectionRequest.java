package com.kizuna.shift.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 当日実績の訂正要求。訂正できる項目の全量を毎回送る（省略は「変更しない」ではなく「値なし」）— 実終了と待機場所は空へ戻す訂正が要るため、部分更新の形では表せない。
 *
 * <p>キャストとシフト参照は載せない。予実の付け替えは実績が物化した事実を破るので、逃げ道は取消 → 再記録に限る（ADR 0014）。
 */
@Data
public class AttendanceCorrectionRequest {

  /** 帰属営業日。日付変更時刻の変更が不遡及であるため、変更前に起きた飛び込みの誤帰属はここで直す。 */
  @NotNull private LocalDate businessDate;

  @NotNull private LocalDateTime actualStartAt;

  private LocalDateTime actualEndAt;

  @Size(max = 200)
  private String waitingPlace;
}
