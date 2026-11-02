package com.kizuna.medium.api.dto;

import com.kizuna.medium.domain.Medium;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class MediumRequests {
  private MediumRequests() {}

  public record CreateRequest(
      @NotBlank(message = "媒体名を入力してください")
          @Size(max = Medium.NAME_MAX, message = "媒体名は" + Medium.NAME_MAX + "文字以内で入力してください")
          String name) {
    public CreateRequest {
      name = Medium.normalizeName(name);
    }
  }

  public record RenameRequest(
      @NotBlank(message = "媒体名を入力してください")
          @Size(max = Medium.NAME_MAX, message = "媒体名は" + Medium.NAME_MAX + "文字以内で入力してください")
          String name,
      @NotNull(message = "版を指定してください") Long version) {
    public RenameRequest {
      name = Medium.normalizeName(name);
    }
  }

  public record VersionRequest(@NotNull(message = "版を指定してください") Long version) {}
}
