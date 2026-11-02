package com.kizuna.advertising.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kizuna.advertising.domain.AdvertisingCategory;
import com.kizuna.advertising.domain.AdvertisingCost;
import com.kizuna.advertising.domain.AdvertisingMedium;
import java.time.OffsetDateTime;

public final class AdvertisingResponses {
  private AdvertisingResponses() {}

  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record CostResponse(
      String id,
      Long storeId,
      String month,
      AdvertisingCategory category,
      MediumResponse medium,
      String agencyName,
      String planName,
      Integer inquiryCount,
      int amount,
      long version,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {
    /** mediumName は媒体の現在名。変更履歴にはこの応答をその時点の写しとして残す。 */
    public static CostResponse of(AdvertisingCost c, String mediumName) {
      return new CostResponse(
          c.getId(),
          c.getStoreId(),
          c.getMonth(),
          c.getCategory(),
          new MediumResponse(c.getMediumId(), mediumName),
          c.getAgencyName(),
          c.getPlanName(),
          c.getInquiryCount(),
          c.getAmount(),
          c.getVersion(),
          c.getCreatedAt(),
          c.getUpdatedAt());
    }
  }

  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record CostSummary(
      String id,
      Long storeId,
      String month,
      AdvertisingCategory category,
      MediumResponse medium,
      String agencyName,
      String planName,
      Integer inquiryCount,
      int amount,
      long version,
      OffsetDateTime updatedAt) {
    public static CostSummary of(AdvertisingCost c, String mediumName) {
      return new CostSummary(
          c.getId(),
          c.getStoreId(),
          c.getMonth(),
          c.getCategory(),
          new MediumResponse(c.getMediumId(), mediumName),
          c.getAgencyName(),
          c.getPlanName(),
          c.getInquiryCount(),
          c.getAmount(),
          c.getVersion(),
          c.getUpdatedAt());
    }
  }

  public record MediumResponse(String id, String name) {
    public static MediumResponse of(AdvertisingMedium medium) {
      return medium == null ? null : new MediumResponse(medium.id(), medium.name());
    }
  }

  public record MonthResponse(
      Long storeId,
      String month,
      long version,
      long entryCount,
      long salesAmount,
      long recruitmentAmount,
      long recordedTotalAmount,
      OffsetDateTime generatedAt) {}

  public record CopyResponse(
      String id,
      Long storeId,
      String sourceMonth,
      String targetMonth,
      int copiedCount,
      long sourceVersion,
      long targetVersion,
      OffsetDateTime createdAt) {}

  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record ChangeSummary(
      String id,
      String costId,
      String action,
      Long actorId,
      OffsetDateTime occurredAt,
      Long versionBefore,
      Long versionAfter) {}

  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record ChangeResponse(
      String id,
      String costId,
      String action,
      Long actorId,
      OffsetDateTime occurredAt,
      Long versionBefore,
      Long versionAfter,
      String reason,
      String sourceCostId,
      CostResponse before,
      CostResponse after) {}
}
