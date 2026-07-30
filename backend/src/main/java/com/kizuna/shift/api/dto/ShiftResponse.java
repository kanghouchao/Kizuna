package com.kizuna.shift.api.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftResponse {
  private String id;
  private String castId;
  private LocalDate workDate;
  private LocalTime startTime;
  private LocalTime endTime;
  private String status;
  private boolean publicVisible;
  private String approvedBy;
  private OffsetDateTime approvedAt;
  private boolean attendanceConfirmed;
  private LocalTime actualStartTime;
  private LocalTime actualEndTime;
  private String actualRecordedBy;
  private OffsetDateTime actualRecordedAt;
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;
}
