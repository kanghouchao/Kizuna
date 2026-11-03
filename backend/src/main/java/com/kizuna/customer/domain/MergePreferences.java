package com.kizuna.customer.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 優先指定も {@link MergeProfile} と同じく全項目を明示させる（欠落は 400、{@code null} は指定なし）。 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record MergePreferences(
    @JsonProperty(required = true) String phone,
    @JsonProperty(required = true) String email,
    @JsonProperty(required = true) String line) {
  public String selected(ContactType type) {
    return switch (type) {
      case PHONE -> phone;
      case EMAIL -> email;
      case LINE -> line;
    };
  }
}
