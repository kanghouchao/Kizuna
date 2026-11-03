package com.kizuna.remuneration.api.dto;

import com.kizuna.remuneration.application.RemunerationViews.GuaranteeList;
import org.springframework.data.domain.Page;

public record GuaranteeListResponse(long version, Page<GuaranteeResponse> entries) {
  public static GuaranteeListResponse of(GuaranteeList l) {
    return new GuaranteeListResponse(l.version(), l.entries().map(GuaranteeResponse::of));
  }
}
