package com.kizuna.advertising.api.dto;

import com.kizuna.advertising.domain.AdvertisingCategory;
import com.kizuna.advertising.domain.AdvertisingValues;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import tools.jackson.databind.annotation.JsonDeserialize;

public final class AdvertisingRequests {
  private AdvertisingRequests() {}

  public record CreateRequest(
      @NotNull(message = "対象月を指定してください")
          @JsonDeserialize(using = AdvertisingStringDeserializer.class)
          String month,
      @JsonDeserialize(using = AdvertisingCategoryDeserializer.class) AdvertisingCategory category,
      @JsonDeserialize(using = AdvertisingStringDeserializer.class) String mediaName,
      @JsonDeserialize(using = AdvertisingStringDeserializer.class) String agencyName,
      @JsonDeserialize(using = AdvertisingStringDeserializer.class) String planName,
      @JsonDeserialize(using = AdvertisingIntegerDeserializer.class) Integer inquiryCount,
      @JsonDeserialize(using = AdvertisingIntegerDeserializer.class) Integer amount,
      @NotNull(message = "要求識別子を指定してください") UUID requestId) {
    public CreateRequest {
      mediaName = AdvertisingValues.text(mediaName, 200);
      agencyName = AdvertisingValues.text(agencyName, 200);
      planName = AdvertisingValues.text(planName, 200);
    }

    public AdvertisingValues values() {
      return new AdvertisingValues(category, mediaName, agencyName, planName, inquiryCount, amount);
    }
  }

  public record ReplaceRequest(
      @JsonDeserialize(using = AdvertisingCategoryDeserializer.class) AdvertisingCategory category,
      @JsonDeserialize(using = AdvertisingStringDeserializer.class) String mediaName,
      @JsonDeserialize(using = AdvertisingStringDeserializer.Required.class) String agencyName,
      @JsonDeserialize(using = AdvertisingStringDeserializer.Required.class) String planName,
      @JsonDeserialize(using = AdvertisingIntegerDeserializer.Required.class) Integer inquiryCount,
      @JsonDeserialize(using = AdvertisingIntegerDeserializer.class) Integer amount,
      @NotNull(message = "版を指定してください")
          @JsonDeserialize(using = AdvertisingVersionDeserializer.class)
          Long version,
      @JsonDeserialize(using = AdvertisingStringDeserializer.class) String reason,
      @NotNull(message = "要求識別子を指定してください") UUID requestId) {
    public ReplaceRequest {
      mediaName = AdvertisingValues.text(mediaName, 200);
      agencyName = AdvertisingValues.text(agencyName, 200);
      planName = AdvertisingValues.text(planName, 200);
      reason = AdvertisingValues.reason(reason);
    }

    public AdvertisingValues values() {
      return new AdvertisingValues(category, mediaName, agencyName, planName, inquiryCount, amount);
    }
  }

  public record DeleteRequest(
      @NotNull(message = "版を指定してください")
          @JsonDeserialize(using = AdvertisingVersionDeserializer.class)
          Long version,
      @JsonDeserialize(using = AdvertisingStringDeserializer.class) String reason,
      @NotNull(message = "要求識別子を指定してください") UUID requestId) {
    public DeleteRequest {
      reason = AdvertisingValues.reason(reason);
    }
  }

  public record CopyRequest(
      @NotNull(message = "前月の版を指定してください")
          @JsonDeserialize(using = AdvertisingVersionDeserializer.class)
          Long sourceVersion,
      @NotNull(message = "対象月の版を指定してください")
          @JsonDeserialize(using = AdvertisingVersionDeserializer.class)
          Long targetVersion,
      @JsonDeserialize(using = AdvertisingStringDeserializer.class) String reason,
      @NotNull(message = "要求識別子を指定してください") UUID requestId) {
    public CopyRequest {
      reason = AdvertisingValues.reason(reason);
    }
  }
}
