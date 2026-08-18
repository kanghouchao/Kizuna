package com.kizuna.shift.api.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 当日実績の表現。取消済みの行はどの読み口にも現れないため、取消の標記は載せない。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceResponse {
  private String id;
  private String castId;
  private LocalDate businessDate;
  private LocalDateTime actualStartAt;
  private LocalDateTime actualEndAt;

  /** null は飛び込み出勤（予定なし）を意味する。 */
  private String shiftId;

  private String waitingPlace;
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;
}
