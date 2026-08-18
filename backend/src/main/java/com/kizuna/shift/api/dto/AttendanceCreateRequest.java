package com.kizuna.shift.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Data;

/** 当日実績の記録要求。帰属営業日は要求に載せない — シフトからの継承か実開始時刻からの判定でサーバが決める。 */
@Data
public class AttendanceCreateRequest {

  @NotBlank private String castId;

  /** 予定していたシフト。省略は飛び込み出勤（予定なし）の記録になる。 */
  private String shiftId;

  @NotNull private LocalDateTime actualStartAt;

  /** 実際の終了。閉店時にまとめて記入する運用のため、記録の時点では省略できる。 */
  private LocalDateTime actualEndAt;

  @Size(max = 200)
  private String waitingPlace;
}
