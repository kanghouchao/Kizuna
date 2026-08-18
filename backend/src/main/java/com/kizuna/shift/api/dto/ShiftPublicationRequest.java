package com.kizuna.shift.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 公開可否の切替要求。切替は望む状態を明示して送る（相対的な反転にしないのは、逐行の一括操作で取りこぼしを起こさないため）。 */
@Data
public class ShiftPublicationRequest {
  @NotNull private Boolean published;
}
