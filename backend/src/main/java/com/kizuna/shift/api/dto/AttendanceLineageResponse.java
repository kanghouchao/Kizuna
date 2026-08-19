package com.kizuna.shift.api.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 系列照会の末端に来る当日実績。取消済みの行は導出・照会から外れる（ADR 0014）ため、ここに現れるのは 未取消の 1 行だけで、取消の標記も載せない。
 *
 * <p>一覧の {@link AttendanceResponse} との違いは実行主体を持つことである。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceLineageResponse {
  private String id;
  private String castId;
  private LocalDate businessDate;
  private LocalDateTime actualStartAt;
  private LocalDateTime actualEndAt;
  private String waitingPlace;
  private ActorResponse createdBy;
  private ActorResponse updatedBy;
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;
}
