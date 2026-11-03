package com.kizuna.recruitment.api.dto;

import com.kizuna.medium.lookup.MediumReference;

/** 応募者が応募元として指す媒体。名前は現在の媒体名。 */
public record ApplicantMediumResponse(String id, String name) {
  public static ApplicantMediumResponse from(MediumReference medium) {
    return medium == null ? null : new ApplicantMediumResponse(medium.id(), medium.name());
  }
}
