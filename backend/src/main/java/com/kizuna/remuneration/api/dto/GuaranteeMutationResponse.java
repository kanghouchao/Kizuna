package com.kizuna.remuneration.api.dto;

import com.kizuna.remuneration.application.RemunerationViews.GuaranteeMutation;

public record GuaranteeMutationResponse(
    GuaranteeResponse guarantee, long version, String changeId) {
  public static GuaranteeMutationResponse of(GuaranteeMutation m) {
    return new GuaranteeMutationResponse(
        GuaranteeResponse.of(m.guarantee()), m.version(), m.changeId());
  }
}
