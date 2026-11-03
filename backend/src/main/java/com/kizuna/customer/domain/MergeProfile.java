package com.kizuna.customer.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

/**
 * 確定資料は空欄も含めて全項目を明示させる。欠落を「空にする」と取り違えないよう、欠落は 400、{@code null} の明示は空欄として受ける。 統合の証跡（JSON
 * 列）の読み戻しにも同じ必須検査が掛かるため、項目を足すと既存の証跡が読めなくなる。
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record MergeProfile(
    @JsonProperty(required = true) @Size(max = 255) String name,
    @JsonProperty(required = true) @Size(max = 500) String address,
    @JsonProperty(required = true) @Size(max = 255) String buildingName,
    @JsonProperty(required = true) @Size(max = 255) String landmark,
    @JsonProperty(required = true) @Size(max = 50) String classification,
    @JsonProperty(required = true) Boolean hasPet,
    @JsonProperty(required = true) @Size(max = 255) String usageAreas,
    @JsonProperty(required = true) @Size(max = 50) String ngType,
    @JsonProperty(required = true) String ngContent) {
  public static MergeProfile from(Customer c) {
    return new MergeProfile(
        c.getName(),
        c.getAddress(),
        c.getBuildingName(),
        c.getLandmark(),
        c.getClassification(),
        c.getHasPet(),
        c.getUsageAreas(),
        c.getNgType(),
        c.getNgContent());
  }
}
