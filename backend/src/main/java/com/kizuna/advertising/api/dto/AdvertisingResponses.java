package com.kizuna.advertising.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kizuna.advertising.application.AdvertisingService.MonthTotals;
import com.kizuna.advertising.application.AdvertisingViews.ChangeDetail;
import com.kizuna.advertising.application.AdvertisingViews.CopyResult;
import com.kizuna.advertising.application.AdvertisingViews.CostView;
import com.kizuna.advertising.domain.AdvertisingCategory;
import com.kizuna.advertising.domain.AdvertisingChange;
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
    public static CostResponse of(CostView c) {
      return c == null
          ? null
          : new CostResponse(
              c.id(),
              c.storeId(),
              c.month(),
              c.category(),
              MediumResponse.of(c.medium()),
              c.agencyName(),
              c.planName(),
              c.inquiryCount(),
              c.amount(),
              c.version(),
              c.createdAt(),
              c.updatedAt());
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
    public static CostSummary of(CostView c) {
      return new CostSummary(
          c.id(),
          c.storeId(),
          c.month(),
          c.category(),
          MediumResponse.of(c.medium()),
          c.agencyName(),
          c.planName(),
          c.inquiryCount(),
          c.amount(),
          c.version(),
          c.updatedAt());
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
      OffsetDateTime generatedAt) {
    public static MonthResponse of(MonthTotals t) {
      return new MonthResponse(
          t.storeId(),
          t.month(),
          t.version(),
          t.entryCount(),
          t.salesAmount(),
          t.recruitmentAmount(),
          t.recordedTotalAmount(),
          t.generatedAt());
    }
  }

  public record CopyResponse(
      String id,
      Long storeId,
      String sourceMonth,
      String targetMonth,
      int copiedCount,
      long sourceVersion,
      long targetVersion,
      OffsetDateTime createdAt) {
    public static CopyResponse of(CopyResult r) {
      return new CopyResponse(
          r.id(),
          r.storeId(),
          r.sourceMonth(),
          r.targetMonth(),
          r.copiedCount(),
          r.sourceVersion(),
          r.targetVersion(),
          r.createdAt());
    }
  }

  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record ChangeSummary(
      String id,
      String costId,
      String action,
      Long actorId,
      OffsetDateTime occurredAt,
      Long versionBefore,
      Long versionAfter) {
    public static ChangeSummary of(AdvertisingChange c) {
      return new ChangeSummary(
          c.getId(),
          c.getCostId(),
          c.getAction(),
          c.getActorId(),
          c.getCreatedAt(),
          c.getVersionBefore(),
          c.getVersionAfter());
    }
  }

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
      CostResponse after) {
    public static ChangeResponse of(ChangeDetail d) {
      var c = d.change();
      return new ChangeResponse(
          c.getId(),
          c.getCostId(),
          c.getAction(),
          c.getActorId(),
          c.getCreatedAt(),
          c.getVersionBefore(),
          c.getVersionAfter(),
          c.getReason(),
          c.getSourceCostId(),
          CostResponse.of(d.before()),
          CostResponse.of(d.after()));
    }
  }
}
