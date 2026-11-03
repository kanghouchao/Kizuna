package com.kizuna.advertising.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kizuna.advertising.domain.AdvertisingCategory;
import com.kizuna.advertising.domain.AdvertisingValues;
import com.kizuna.receipt.once.Fingerprint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public final class AdvertisingRequests {
  private static final String NON_NEGATIVE = "金額・人数・版は 0 以上の整数で指定してください";

  private AdvertisingRequests() {}

  public record CreateRequest(
      @NotNull(message = "対象月を指定してください") String month,
      AdvertisingCategory category,
      String mediumId,
      String agencyName,
      String planName,
      @PositiveOrZero(message = NON_NEGATIVE) Integer inquiryCount,
      @PositiveOrZero(message = NON_NEGATIVE) Integer amount,
      @NotNull(message = "要求識別子を指定してください") UUID requestId) {
    public CreateRequest {
      mediumId = AdvertisingValues.text(mediumId, 64);
      agencyName = AdvertisingValues.text(agencyName, 200);
      planName = AdvertisingValues.text(planName, 200);
    }

    public AdvertisingValues values() {
      return new AdvertisingValues(category, mediumId, agencyName, planName, inquiryCount, amount);
    }

    public String fingerprint() {
      return Fingerprint.of(
          "CREATE", month, category, mediumId, agencyName, planName, inquiryCount, amount);
    }
  }

  public record ReplaceRequest(
      AdvertisingCategory category,
      String mediumId,
      @JsonProperty(required = true) String agencyName,
      @JsonProperty(required = true) String planName,
      @JsonProperty(required = true) @PositiveOrZero(message = NON_NEGATIVE) Integer inquiryCount,
      @PositiveOrZero(message = NON_NEGATIVE) Integer amount,
      @NotNull(message = "版を指定してください") @PositiveOrZero(message = NON_NEGATIVE) Long version,
      String reason,
      @NotNull(message = "要求識別子を指定してください") UUID requestId) {
    public ReplaceRequest {
      mediumId = AdvertisingValues.text(mediumId, 64);
      agencyName = AdvertisingValues.text(agencyName, 200);
      planName = AdvertisingValues.text(planName, 200);
      reason = AdvertisingValues.reason(reason);
    }

    public AdvertisingValues values() {
      return new AdvertisingValues(category, mediumId, agencyName, planName, inquiryCount, amount);
    }

    public String fingerprint(String id) {
      return Fingerprint.of(
          "REPLACE",
          id,
          version,
          category,
          mediumId,
          agencyName,
          planName,
          inquiryCount,
          amount,
          reason);
    }
  }

  public record DeleteRequest(
      @NotNull(message = "版を指定してください") @PositiveOrZero(message = NON_NEGATIVE) Long version,
      String reason,
      @NotNull(message = "要求識別子を指定してください") UUID requestId) {
    public DeleteRequest {
      reason = AdvertisingValues.reason(reason);
    }

    public String fingerprint(String id) {
      return Fingerprint.of("DELETE", id, version, reason);
    }
  }

  public record CopyRequest(
      @NotNull(message = "前月の版を指定してください") @PositiveOrZero(message = NON_NEGATIVE)
          Long sourceVersion,
      @NotNull(message = "対象月の版を指定してください") @PositiveOrZero(message = NON_NEGATIVE)
          Long targetVersion,
      String reason,
      @NotNull(message = "要求識別子を指定してください") UUID requestId) {
    public CopyRequest {
      reason = AdvertisingValues.reason(reason);
    }

    public String fingerprint(String targetMonth) {
      return Fingerprint.of("COPY", targetMonth, sourceVersion, targetVersion, reason);
    }
  }
}
