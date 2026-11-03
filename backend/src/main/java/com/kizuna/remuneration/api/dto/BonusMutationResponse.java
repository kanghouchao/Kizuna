package com.kizuna.remuneration.api.dto;

import com.kizuna.remuneration.application.RemunerationViews.BonusMutation;

public record BonusMutationResponse(BonusResponse bonus, String changeId) {
  public static BonusMutationResponse of(BonusMutation m) {
    return new BonusMutationResponse(BonusResponse.of(m.bonus()), m.changeId());
  }
}
