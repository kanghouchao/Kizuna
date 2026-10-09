package com.kizuna.advertising.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kizuna.advertising.domain.AdvertisingCategory;
import com.kizuna.advertising.domain.AdvertisingCost;
import java.time.OffsetDateTime;

public final class AdvertisingResponses {
  private AdvertisingResponses() {}

  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record CostResponse(
      String id,
      Long storeId,
      String month,
      AdvertisingCategory category,
      String mediaName,
      String agencyName,
      String planName,
      Integer inquiryCount,
      int amount,
      long version,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {
    public static CostResponse of(AdvertisingCost c) {
      return new CostResponse(
          c.getId(),
          c.getStoreId(),
          c.getMonth(),
          c.getCategory(),
          c.getMediaName(),
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
      String mediaName,
      String agencyName,
      String planName,
      Integer inquiryCount,
      int amount,
      long version,
      OffsetDateTime updatedAt) {
    public static CostSummary of(AdvertisingCost c) {
      return new CostSummary(
          c.getId(),
          c.getStoreId(),
          c.getMonth(),
          c.getCategory(),
          c.getMediaName(),
          c.getAgencyName(),
          c.getPlanName(),
          c.getInquiryCount(),
          c.getAmount(),
          c.getVersion(),
          c.getUpdatedAt());
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
